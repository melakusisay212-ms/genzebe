package com.genzebe.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One bank or wallet account, found from the SMS. Balance edits are stored here. */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val accountKey: String,
    val provider: String,
    val label: String,
    /** Balance typed in by the user. It wins until a newer SMS arrives. */
    val manualBalanceCents: Long? = null,
    val manualAtMillis: Long? = null
)

/**
 * One transaction. [flow] is the direction of the money ("IN" or "OUT") exactly as the SMS said.
 * [kind] is what the user sees ("INCOME", "EXPENSE" or "TRANSFER") and can change.
 * [totalCents] is what really moved: principal plus fee for money out.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["occurredAt"]),
        Index(value = ["accountKey"])
    ]
)
data class TxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dedupeKey: String,
    val accountKey: String,
    val provider: String,
    val flow: String,
    val kind: String,
    val principalCents: Long,
    val feeCents: Long,
    val totalCents: Long,
    val balanceAfterCents: Long?,
    val counterparty: String?,
    val counterpartyInstitution: Boolean,
    val memo: String?,
    val txnId: String?,
    /** When the transaction happened, epoch milliseconds. */
    val occurredAt: Long,
    val receivedAt: Long,
    val rawBody: String,
    val needsReview: Boolean,
    val reviewReason: String?,
    /** True when the balance does not follow from the previous SMS: a message may be missing. */
    val gapBefore: Boolean = false,
    /** True once the user has looked at it and accepted it. */
    val reviewed: Boolean = false,
    val transferGroup: String? = null,
    /** True when the user changed the type, so automatic matching leaves it alone. */
    val userEdited: Boolean = false
)

/** Full names of the account holder, learned from SMS greetings. Used to spot own-account transfers. */
@Entity(tableName = "owner_names")
data class OwnerNameEntity(@PrimaryKey val name: String)
