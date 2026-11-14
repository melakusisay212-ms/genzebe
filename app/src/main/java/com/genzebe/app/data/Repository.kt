package com.genzebe.app.data

import androidx.room.withTransaction
import com.genzebe.app.core.BalanceChain
import com.genzebe.app.core.MatchCandidate
import com.genzebe.app.core.MoneyFlow
import com.genzebe.app.core.ParseResult
import com.genzebe.app.core.ParsedTransaction
import com.genzebe.app.core.SmsParser
import com.genzebe.app.core.TransferMatcher
import com.genzebe.app.core.TxKind
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class IngestOutcome { ADDED, DUPLICATE, IGNORED, UNRECOGNIZED }

data class RawSms(val sender: String?, val body: String, val receivedAtMillis: Long)

data class ImportSummary(
    val scanned: Int,
    val added: Int,
    val duplicates: Int,
    val ignored: Int,
    val unrecognized: Int
)

/**
 * The only place that writes transactions. It turns an SMS into one record, drops duplicates,
 * checks the balance chain, and pairs transfers between the user's own accounts.
 */
class Repository(private val db: AppDatabase) {
    private val dao = db.dao()

    fun observeTransactions(): Flow<List<TxEntity>> = dao.observeTransactions()

    fun observeAccounts(): Flow<List<AccountEntity>> = dao.observeAccounts()

    private data class Inserted(
        val outcome: IngestOutcome,
        val accountKey: String? = null,
        val occurredAt: Long = 0L
    )

    /** Handles one live SMS. */
    suspend fun ingest(sender: String?, body: String, receivedAtMillis: Long): IngestOutcome =
        db.withTransaction {
            val inserted = insertOne(sender, body, receivedAtMillis)
            if (inserted.outcome == IngestOutcome.ADDED && inserted.accountKey != null) {
                recomputeGaps(inserted.accountKey)
                matchTransfers(inserted.occurredAt)
            }
            inserted.outcome
        }

    /** Handles many SMS at once, for example the inbox of the current Ethiopian month. */
    suspend fun ingestAll(items: List<RawSms>, onProgress: (Int, Int) -> Unit = { _, _ -> }): ImportSummary {
        var added = 0
        var duplicates = 0
        var ignored = 0
        var unrecognized = 0
        val touched = HashSet<String>()
        val ordered = items.sortedBy { it.receivedAtMillis }
        db.withTransaction {
            for ((index, sms) in ordered.withIndex()) {
                val r = insertOne(sms.sender, sms.body, sms.receivedAtMillis)
                when (r.outcome) {
                    IngestOutcome.ADDED -> {
                        added++
                        r.accountKey?.let { touched.add(it) }
                    }
                    IngestOutcome.DUPLICATE -> duplicates++
                    IngestOutcome.IGNORED -> ignored++
                    IngestOutcome.UNRECOGNIZED -> unrecognized++
                }
                if (index % 50 == 0) onProgress(index, ordered.size)
            }
            for (key in touched) recomputeGaps(key)
            if (added > 0) matchTransfers(null)
        }
        onProgress(ordered.size, ordered.size)
        return ImportSummary(ordered.size, added, duplicates, ignored, unrecognized)
    }

    private suspend fun insertOne(sender: String?, body: String, receivedAt: Long): Inserted {
        return when (val result = SmsParser.parse(sender, body)) {
            is ParseResult.Ignored -> Inserted(IngestOutcome.IGNORED)
            is ParseResult.Unrecognized -> Inserted(IngestOutcome.UNRECOGNIZED)
            is ParseResult.Parsed -> {
                val p = result.tx
                val occurred = p.occurredAt?.toEpochMilli() ?: receivedAt
                val kind = if (p.flow == MoneyFlow.OUT) TxKind.EXPENSE else TxKind.INCOME
                val entity = TxEntity(
                    dedupeKey = dedupeKey(p, occurred),
                    accountKey = p.accountKey,
                    provider = p.provider.name,
                    flow = p.flow.name,
                    kind = kind.name,
                    principalCents = p.principalCents,
                    feeCents = p.feeCents,
                    totalCents = p.totalCents,
                    balanceAfterCents = p.balanceAfterCents,
                    counterparty = p.counterparty,
                    counterpartyInstitution = p.counterpartyIsInstitution,
                    memo = p.memo,
                    txnId = p.txnId,
                    occurredAt = occurred,
                    receivedAt = receivedAt,
                    rawBody = body,
                    needsReview = p.needsReview,
                    reviewReason = p.reviewReason
                )
                val id = dao.insertTx(entity)
                if (id == -1L) {
                    Inserted(IngestOutcome.DUPLICATE)
                } else {
                    dao.insertAccount(AccountEntity(p.accountKey, p.provider.name, p.accountLabel))
                    for (name in p.ownerNames) dao.insertOwnerName(OwnerNameEntity(name.trim()))
                    Inserted(IngestOutcome.ADDED, p.accountKey, occurred)
                }
            }
        }
    }

    /** Same SMS twice, or the same transaction announced twice, must never count twice. */
    private fun dedupeKey(p: ParsedTransaction, occurred: Long): String =
        if (p.txnId != null) {
            "${p.provider.name}|${p.txnId}"
        } else {
            "${p.provider.name}|${p.accountKey}|${p.flow.name}|${p.totalCents}|${p.balanceAfterCents ?: occurred}"
        }

    /** Re-checks, in time order, whether each balance follows from the one before it. */
    private suspend fun recomputeGaps(accountKey: String) {
        var previous: Long? = null
        for (tx in dao.txForAccountAsc(accountKey)) {
            val gap = BalanceChain.hasGap(
                previous, MoneyFlow.valueOf(tx.flow), tx.totalCents, tx.balanceAfterCents
            )
            if (gap != tx.gapBefore) dao.updateTx(tx.copy(gapBefore = gap))
            previous = tx.balanceAfterCents
        }
    }

    /**
     * Pairs money leaving one of your accounts with money arriving in another, and marks incoming
     * money from your own name as a transfer. [around] limits the work to one moment in time; null
     * checks everything.
     */
    private suspend fun matchTransfers(around: Long?) {
        val owners = dao.ownerNames()
        val pool = if (around == null) {
            dao.allTxAsc()
        } else {
            dao.txBetween(around - 2 * TransferMatcher.WINDOW_MS, around + 2 * TransferMatcher.WINDOW_MS)
        }
        val open = pool.filter { it.kind != TxKind.TRANSFER.name && !it.userEdited }
        if (open.isEmpty()) return

        val candidates = open.map {
            MatchCandidate(
                id = it.id,
                accountKey = it.accountKey,
                flow = MoneyFlow.valueOf(it.flow),
                principalCents = it.principalCents,
                timeMillis = it.occurredAt,
                counterparty = it.counterparty,
                counterpartyIsInstitution = it.counterpartyInstitution
            )
        }
        val byId = open.associateBy { it.id }
        val paired = HashSet<Long>()

        for ((outId, inId) in TransferMatcher.pairs(candidates, owners)) {
            val group = UUID.randomUUID().toString()
            byId[outId]?.let { dao.updateTx(it.copy(kind = TxKind.TRANSFER.name, transferGroup = group)) }
            byId[inId]?.let { dao.updateTx(it.copy(kind = TxKind.TRANSFER.name, transferGroup = group)) }
            paired.add(outId)
            paired.add(inId)
        }
        for (c in candidates) {
            if (c.id in paired) continue
            if (TransferMatcher.isOwnIncoming(c, owners)) {
                byId[c.id]?.let {
                    dao.updateTx(it.copy(kind = TxKind.TRANSFER.name, transferGroup = "own-${c.id}"))
                }
            }
        }
    }

    // ---------------------------------------------------------------- edits by the user

    suspend fun setKind(id: Long, kind: TxKind) {
        db.withTransaction {
            val tx = dao.txById(id)
            if (tx != null) {
                val group = tx.transferGroup
                if (tx.kind == TxKind.TRANSFER.name && kind != TxKind.TRANSFER && group != null) {
                    // undoing a transfer frees the other half too
                    for (partner in dao.txByGroup(group)) {
                        if (partner.id == id) continue
                        dao.updateTx(
                            partner.copy(
                                kind = defaultKind(partner.flow).name,
                                transferGroup = null,
                                userEdited = true
                            )
                        )
                    }
                }
                dao.updateTx(
                    tx.copy(
                        kind = kind.name,
                        userEdited = true,
                        reviewed = true,
                        transferGroup = if (kind == TxKind.TRANSFER) group else null
                    )
                )
            }
        }
    }

    suspend fun confirm(id: Long) {
        val tx = dao.txById(id) ?: return
        dao.updateTx(tx.copy(reviewed = true))
    }

    suspend fun delete(id: Long) {
        db.withTransaction {
            val tx = dao.txById(id)
            if (tx != null) {
                dao.deleteTx(id)
                recomputeGaps(tx.accountKey)
            }
        }
    }

    suspend fun changeDate(id: Long, newOccurredAt: Long) {
        db.withTransaction {
            val tx = dao.txById(id)
            if (tx != null) {
                dao.updateTx(tx.copy(occurredAt = newOccurredAt))
                recomputeGaps(tx.accountKey)
            }
        }
    }

    suspend fun setManualBalance(accountKey: String, cents: Long, nowMillis: Long) {
        val account = dao.account(accountKey) ?: return
        dao.updateAccount(account.copy(manualBalanceCents = cents, manualAtMillis = nowMillis))
    }

    suspend fun clearAll() {
        db.withTransaction {
            dao.deleteAllTx()
            dao.deleteAllAccounts()
            dao.deleteAllOwnerNames()
        }
    }

    private fun defaultKind(flow: String): TxKind = if (flow == MoneyFlow.OUT.name) TxKind.EXPENSE else TxKind.INCOME
}
