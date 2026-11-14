package com.genzebe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genzebe.app.core.EthiopianCalendar
import com.genzebe.app.core.Money
import com.genzebe.app.core.TxKind
import com.genzebe.app.data.TxEntity
import java.time.LocalDate

/**
 * Ledger: account cards on top (balance, editable) act as the filter. Below them the transactions,
 * grouped by Ethiopian date with that day's total next to the date.
 */
@Composable
fun LedgerScreen(
    vm: MainViewModel,
    onOpenTx: (TxEntity) -> Unit,
    onEditBalance: (accountKey: String, label: String, currentCents: Long?) -> Unit
) {
    val txs by vm.transactions.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val labels = remember(accounts) { accounts.associate { it.accountKey to it.label } }

    var selectedAccount by rememberSaveable { mutableStateOf<String?>(null) }
    var typeFilter by rememberSaveable { mutableStateOf("ALL") }
    var reviewOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(txs, selectedAccount, typeFilter, reviewOnly, query) {
        val q = query.trim().lowercase()
        txs.filter { tx ->
            (selectedAccount == null || tx.accountKey == selectedAccount) &&
                (typeFilter == "ALL" || tx.kind == typeFilter) &&
                (!reviewOnly || tx.needsAttention()) &&
                (q.isEmpty() || tx.title().lowercase().contains(q) ||
                    (tx.memo?.lowercase()?.contains(q) == true) ||
                    Money.format(tx.totalCents).contains(q))
        }
    }
    val groups = remember(filtered) {
        filtered
            .groupBy { EthiopianCalendar.localDateOf(it.occurredAt) }
            .toList()
            .sortedByDescending { it.first }
    }
    val reviewCount = remember(txs) { txs.count { it.needsAttention() } }
    val totalBalance = accounts.sumOf { it.balanceCents ?: 0L }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Ledger", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = G.Ink)
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    AccountCard(
                        title = "All accounts",
                        balanceText = if (vm.hideBalance) "••••" else Money.format(totalBalance),
                        selected = selectedAccount == null,
                        editable = false,
                        onClick = { selectedAccount = null },
                        onEdit = {}
                    )
                }
                items(accounts, key = { it.accountKey }) { acc ->
                    AccountCard(
                        title = acc.label,
                        balanceText = when {
                            vm.hideBalance -> "••••"
                            acc.balanceCents == null -> "No balance yet"
                            else -> Money.format(acc.balanceCents)
                        },
                        selected = selectedAccount == acc.accountKey,
                        editable = true,
                        onClick = { selectedAccount = acc.accountKey },
                        onEdit = { onEditBalance(acc.accountKey, acc.label, acc.balanceCents) }
                    )
                }
            }
        }

        item {
            SearchBox(query = query, onChange = { query = it })
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { GChip("All", typeFilter == "ALL", { typeFilter = "ALL" }, compact = true) }
                item { GChip("Expense", typeFilter == TxKind.EXPENSE.name, { typeFilter = TxKind.EXPENSE.name }, compact = true) }
                item { GChip("Income", typeFilter == TxKind.INCOME.name, { typeFilter = TxKind.INCOME.name }, compact = true) }
                item { GChip("Transfer", typeFilter == TxKind.TRANSFER.name, { typeFilter = TxKind.TRANSFER.name }, compact = true) }
                if (reviewCount > 0) {
                    item {
                        GChip("Check ($reviewCount)", reviewOnly, { reviewOnly = !reviewOnly }, compact = true)
                    }
                }
            }
        }

        if (groups.isEmpty()) {
            item {
                GCard(padding = 20.dp) {
                    Text(
                        text = if (txs.isEmpty()) "Nothing here yet. Transactions appear when a bank or wallet SMS arrives."
                        else "No transactions match these filters.",
                        fontSize = 14.sp,
                        color = G.Muted
                    )
                }
            }
        }

        for ((date, dayTxs) in groups) {
            item(key = "h-$date") {
                DateHeader(date = date, dayTxs = dayTxs)
            }
            item(key = "c-$date") {
                GCard(padding = 14.dp) {
                    dayTxs.forEachIndexed { index, tx ->
                        TxRow(
                            tx = tx,
                            accountLabel = labels[tx.accountKey] ?: tx.accountKey,
                            ethiopianClock = vm.ethiopianClock,
                            onClick = { onOpenTx(tx) }
                        )
                        if (index < dayTxs.size - 1) RowDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate, dayTxs: List<TxEntity>) {
    val expense = dayTxs.filter { it.kind == TxKind.EXPENSE.name }.sumOf { it.totalCents }
    val income = dayTxs.filter { it.kind == TxKind.INCOME.name }.sumOf { it.totalCents }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = Fmt.dayLabel(date),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = G.Ink,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (expense > 0) Pill("−" + Money.format(expense), G.ExpenseBg, G.Expense)
        if (income > 0) Pill("+" + Money.format(income), G.IncomeBg, G.Income)
    }
}

@Composable
private fun AccountCard(
    title: String,
    balanceText: String,
    selected: Boolean,
    editable: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .width(168.dp)
            .clip(shape)
            .background(if (selected) G.Hero else G.Surface)
            .border(1.dp, if (selected) G.Hero else G.Border, shape)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) G.HeroMuted else G.Muted,
            maxLines = 1
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = balanceText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) androidx.compose.ui.graphics.Color.White else G.Ink,
            maxLines = 1
        )
        Spacer(Modifier.height(6.dp))
        if (editable) {
            Text(
                text = "Edit balance",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) G.Lime else G.Accent,
                modifier = Modifier.clickable(onClick = onEdit).padding(vertical = 6.dp)
            )
        } else {
            Text("Total", fontSize = 12.sp, color = if (selected) G.HeroMuted else G.Muted, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(G.Surface)
            .border(1.dp, G.Border, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (query.isEmpty()) {
            Text("Search name or amount", fontSize = 14.sp, color = G.Muted)
        }
        BasicTextField(
            value = query,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = G.Ink),
            cursorBrush = SolidColor(G.Accent),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
