package com.genzebe.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.genzebe.app.core.EthiopianCalendar
import com.genzebe.app.core.Money
import kotlinx.coroutines.delay

private enum class Tab(val label: String) { HOME("Home"), CALENDAR("Calendar"), LEDGER("Ledger"), ANALYSIS("Analysis") }

private data class BalanceEdit(val key: String, val label: String, val cents: Long?)

@Composable
fun AppRoot(sharedText: String? = null) {
    val vm: MainViewModel = viewModel()

    if (!vm.onboardingDone) {
        Box(Modifier.fillMaxSize().background(G.Ground).statusBarsPadding().navigationBarsPadding()) {
            OnboardingScreen(vm)
        }
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.HOME.name) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var selectedTxId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var balanceEdit by remember { mutableStateOf<BalanceEdit?>(null) }
    var pendingShare by rememberSaveable { mutableStateOf(sharedText) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Keeps "today" correct if the app stays open past midnight.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }

    val txs by vm.transactions.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val labels = remember(accounts) { accounts.associate { it.accountKey to it.label } }
    val selectedTx = txs.firstOrNull { it.id == selectedTxId }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = !showSettings && tab != Tab.HOME.name) { tab = Tab.HOME.name }

    // A message shared to the app is added once and the note is shown.
    LaunchedEffect(pendingShare) {
        val text = pendingShare
        if (!text.isNullOrBlank()) {
            vm.addPastedMessage(text)
            pendingShare = null
        }
    }
    LaunchedEffect(vm.notice) {
        if (vm.notice != null) {
            delay(2800)
            vm.clearNotice()
        }
    }

    Column(Modifier.fillMaxSize().background(G.Ground).statusBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showSettings) {
                SettingsScreen(vm, onBack = { showSettings = false })
            } else {
                when (tab) {
                    Tab.HOME.name -> HomeScreen(
                        vm = vm,
                        nowMillis = now,
                        onOpenLedger = { tab = Tab.LEDGER.name },
                        onOpenSettings = { showSettings = true },
                        onOpenTx = { selectedTxId = it.id }
                    )
                    Tab.CALENDAR.name -> CalendarScreen(vm, now) { selectedTxId = it.id }
                    Tab.LEDGER.name -> LedgerScreen(
                        vm = vm,
                        onOpenTx = { selectedTxId = it.id },
                        onEditBalance = { key, label, cents -> balanceEdit = BalanceEdit(key, label, cents) }
                    )
                    else -> AnalysisScreen(vm, now)
                }
            }

            // Import progress and short notes
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val state = vm.importState
                val importText: String? = when (state) {
                    is ImportState.Running -> if (state.total > 0) "Reading messages ${state.done} of ${state.total}" else "Reading messages"
                    is ImportState.Done -> "Imported ${state.summary.added} transactions"
                    is ImportState.Failed -> state.message
                    ImportState.Idle -> null
                }
                if (importText != null) {
                    Banner(importText, onClick = { if (state !is ImportState.Running) vm.dismissImportResult() })
                }
                vm.notice?.let { Banner(it, onClick = { vm.clearNotice() }) }
            }
        }

        if (!showSettings) {
            NavBar(selected = tab, onSelect = { tab = it })
        }
    }

    selectedTx?.let { tx ->
        TxDetailDialog(
            tx = tx,
            accountLabel = labels[tx.accountKey] ?: tx.accountKey,
            ethiopianClock = vm.ethiopianClock,
            onDismiss = { selectedTxId = null },
            onSetKind = { vm.setKind(tx.id, it) },
            onConfirm = { vm.confirm(tx.id) },
            onPickDate = { pickingDate = true },
            onDelete = { vm.delete(tx.id); selectedTxId = null }
        )
        if (pickingDate) {
            EthDatePickerDialog(
                initialMillis = tx.occurredAt,
                onDismiss = { pickingDate = false },
                onPicked = { y, m, d ->
                    val oldDate = EthiopianCalendar.localDateOf(tx.occurredAt)
                    val timeOfDay = tx.occurredAt - EthiopianCalendar.startOfDayMillis(oldDate)
                    val newDate = EthiopianCalendar.toLocalDate(com.genzebe.app.core.EthiopianDate(y, m, d))
                    vm.changeDate(tx.id, EthiopianCalendar.startOfDayMillis(newDate) + timeOfDay)
                    pickingDate = false
                }
            )
        }
    }

    balanceEdit?.let { edit ->
        EditBalanceDialog(
            label = edit.label,
            currentCents = edit.cents,
            onDismiss = { balanceEdit = null },
            onSave = { vm.setBalance(edit.key, it); balanceEdit = null }
        )
    }
}

@Composable
private fun Banner(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = androidx.compose.ui.graphics.Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(G.Hero)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    )
}

@Composable
private fun NavBar(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(G.Surface)
            .navigationBarsPadding()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (t in Tab.values()) {
            val on = selected == t.name
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clickable { onSelect(t.name) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = t.label,
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) androidx.compose.ui.graphics.Color.White else G.Muted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (on) G.Hero else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}
