package com.genzebe.app.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.genzebe.app.GenzebeApp
import com.genzebe.app.core.EthiopianCalendar
import com.genzebe.app.core.TxKind
import com.genzebe.app.data.AccountEntity
import com.genzebe.app.data.CsvExport
import com.genzebe.app.data.ImportSummary
import com.genzebe.app.data.IngestOutcome
import com.genzebe.app.data.Prefs
import com.genzebe.app.data.TxEntity
import com.genzebe.app.sms.InboxImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One account as the screens show it. The balance is the newest SMS balance unless the user edited it later. */
data class AccountUi(
    val accountKey: String,
    val label: String,
    val balanceCents: Long?,
    val txCount: Int
)

sealed class ImportState {
    object Idle : ImportState()
    data class Running(val done: Int, val total: Int) : ImportState()
    data class Done(val summary: ImportSummary) : ImportState()
    data class Failed(val message: String) : ImportState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as GenzebeApp
    private val repo = app.repo
    private val prefs: Prefs = app.prefs

    val transactions: StateFlow<List<TxEntity>> =
        repo.observeTransactions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val accountRows: StateFlow<List<AccountEntity>> =
        repo.observeAccounts().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accounts: StateFlow<List<AccountUi>> =
        combine(transactions, accountRows) { txs, rows -> buildAccounts(txs, rows) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var onboardingDone by mutableStateOf(prefs.onboardingDone)
        private set
    var ethiopianClock by mutableStateOf(prefs.ethiopianClock)
        private set
    var hideBalance by mutableStateOf(prefs.hideBalance)
        private set
    var importState: ImportState by mutableStateOf(ImportState.Idle)
        private set
    var notice: String? by mutableStateOf(null)
        private set

    private fun buildAccounts(txs: List<TxEntity>, rows: List<AccountEntity>): List<AccountUi> {
        val byAccount = txs.groupBy { it.accountKey }
        return rows.map { account ->
            val mine = byAccount[account.accountKey].orEmpty()
            val latest = mine.filter { it.balanceAfterCents != null }
                .maxWithOrNull(compareBy<TxEntity>({ it.occurredAt }, { it.id }))
            val manual = account.manualBalanceCents
            val manualAt = account.manualAtMillis
            val useManual = manual != null && manualAt != null && (latest == null || manualAt >= latest.occurredAt)
            AccountUi(
                accountKey = account.accountKey,
                label = account.label,
                balanceCents = if (useManual) manual else latest?.balanceAfterCents,
                txCount = mine.size
            )
        }
    }

    // ---------------------------------------------------------------- settings

    fun setEthiopianClock(value: Boolean) {
        prefs.ethiopianClock = value
        ethiopianClock = value
    }

    fun setHideBalance(value: Boolean) {
        prefs.hideBalance = value
        hideBalance = value
    }

    fun clearNotice() {
        notice = null
    }

    // ---------------------------------------------------------------- first launch

    /**
     * Finishes onboarding. MONTH means read the messages already on the phone from day 1 of the
     * current Ethiopian month. TODAY means ignore everything before now.
     */
    fun finishOnboarding(mode: String) {
        val now = System.currentTimeMillis()
        val since = if (mode == Prefs.START_MONTH) {
            val eth = EthiopianCalendar.today(now)
            EthiopianCalendar.startOfMonthMillis(eth.year, eth.month)
        } else {
            now
        }
        prefs.startMode = mode
        prefs.startFromMillis = since
        prefs.onboardingDone = true
        onboardingDone = true
        if (mode == Prefs.START_MONTH && InboxImporter.hasPermission(app)) importInbox(since)
    }

    /** Reads the SMS inbox from [sinceMillis]. Safe to run again: duplicates are skipped. */
    fun importInbox(sinceMillis: Long) {
        viewModelScope.launch {
            importState = ImportState.Running(0, 0)
            try {
                val items = InboxImporter.read(app, sinceMillis)
                val summary = repo.ingestAll(items) { done, total ->
                    importState = ImportState.Running(done, total)
                }
                importState = ImportState.Done(summary)
            } catch (e: Exception) {
                importState = ImportState.Failed(e.message ?: "Could not read messages")
            }
        }
    }

    /** Re-reads this Ethiopian month from the inbox. */
    fun importThisMonth() {
        val eth = EthiopianCalendar.today()
        importInbox(EthiopianCalendar.startOfMonthMillis(eth.year, eth.month))
    }

    fun dismissImportResult() {
        importState = ImportState.Idle
    }

    // ---------------------------------------------------------------- edits

    fun setKind(id: Long, kind: TxKind) {
        viewModelScope.launch { repo.setKind(id, kind) }
    }

    fun confirm(id: Long) {
        viewModelScope.launch { repo.confirm(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }

    fun changeDate(id: Long, newMillis: Long) {
        viewModelScope.launch { repo.changeDate(id, newMillis) }
    }

    fun setBalance(accountKey: String, cents: Long) {
        viewModelScope.launch { repo.setManualBalance(accountKey, cents, System.currentTimeMillis()) }
    }

    fun clearAll() {
        viewModelScope.launch {
            repo.clearAll()
            notice = "All data removed"
        }
    }

    /** Adds one SMS pasted or copied by the user. */
    fun addPastedMessage(text: String) {
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { repo.ingest(null, text, System.currentTimeMillis()) }
            notice = when (outcome) {
                IngestOutcome.ADDED -> "Message saved"
                IngestOutcome.DUPLICATE -> "Already saved"
                IngestOutcome.IGNORED -> "Not a transaction, skipped"
                IngestOutcome.UNRECOGNIZED -> "Could not read this message"
            }
        }
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val labels = accounts.value.associate { it.accountKey to it.label }
            val text = CsvExport.build(transactions.value, labels)
            try {
                app.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                notice = "Exported ${transactions.value.size} transactions"
            } catch (e: Exception) {
                notice = "Could not save the file"
            }
        }
    }
}
