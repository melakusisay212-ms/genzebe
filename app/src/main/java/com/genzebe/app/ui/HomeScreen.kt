package com.genzebe.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genzebe.app.core.EthiopianCalendar
import com.genzebe.app.core.Money
import com.genzebe.app.core.Stats
import com.genzebe.app.core.TxKind
import com.genzebe.app.data.TxEntity

/**
 * Home is kept calm: total balance, this month's income and expense, two comparisons, and the
 * latest three transactions. Accounts live in the Ledger.
 */
@Composable
fun HomeScreen(
    vm: MainViewModel,
    nowMillis: Long,
    onOpenLedger: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTx: (TxEntity) -> Unit
) {
    val txs by vm.transactions.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val lite = remember(txs) { txs.map { it.toLite() } }
    val labels = remember(accounts) { accounts.associate { it.accountKey to it.label } }

    val today = EthiopianCalendar.localDateOf(nowMillis)
    val eth = EthiopianCalendar.fromLocalDate(today)
    val income = Stats.monthSum(lite, TxKind.INCOME, eth.year, eth.month)
    val expense = Stats.monthSum(lite, TxKind.EXPENSE, eth.year, eth.month)
    val week = Stats.weekComparison(lite, TxKind.EXPENSE, today)
    val mtd = Stats.monthToDateComparison(lite, TxKind.EXPENSE, today)
    val total = accounts.sumOf { it.balanceCents ?: 0L }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = EthiopianCalendar.format(eth) + " · " + Fmt.weekdayName(today),
                    fontSize = 12.sp,
                    color = G.Muted
                )
                Text("ገንዘቤ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = G.Ink)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(G.Surface)
                    .border(1.dp, G.Border, CircleShape)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = G.Ink)
            }
        }

        // Total balance
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(G.Hero)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total balance", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = G.HeroMuted)
                Text(
                    text = if (vm.hideBalance) "Show" else "Hide",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = G.HeroMuted,
                    modifier = Modifier
                        .clickable { vm.setHideBalance(!vm.hideBalance) }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("ETB", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = G.HeroMuted)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (vm.hideBalance) "••••••" else Money.format(total),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        }

        // This month
        GCard(padding = 18.dp) {
            Text(
                text = EthiopianCalendar.monthLabel(eth.year, eth.month),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = G.Muted
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Income", fontSize = 12.sp, color = G.Muted)
                    Text(
                        text = "+" + Money.format(income),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = G.Income
                    )
                }
                Box(Modifier.width(1.dp).height(40.dp).background(G.Border))
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Expense", fontSize = 12.sp, color = G.Muted)
                    Text(
                        text = "−" + Money.format(expense),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = G.Expense
                    )
                }
            }
        }

        // Comparisons
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GCard(modifier = Modifier.weight(1f), padding = 14.dp) {
                Text("This week", fontSize = 12.sp, color = G.Muted)
                Text(
                    text = Fmt.compact(week.currentCents),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = G.Ink
                )
                DeltaLabel(week, "vs last week")
            }
            GCard(modifier = Modifier.weight(1f), padding = 14.dp) {
                Text("Month to date", fontSize = 12.sp, color = G.Muted)
                Text(
                    text = Fmt.compact(mtd.currentCents),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = G.Ink
                )
                DeltaLabel(mtd, "vs last month")
            }
        }

        // Latest
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("Latest")
                Text(
                    text = "See all",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = G.Accent,
                    modifier = Modifier.clickable(onClick = onOpenLedger).padding(vertical = 8.dp)
                )
            }
            GCard(padding = 16.dp) {
                val latest = txs.take(3)
                if (latest.isEmpty()) {
                    Text(
                        text = "No transactions yet. They appear here when a bank or wallet SMS arrives.",
                        fontSize = 14.sp,
                        color = G.Muted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                latest.forEachIndexed { index, tx ->
                    TxRow(
                        tx = tx,
                        accountLabel = labels[tx.accountKey] ?: tx.accountKey,
                        ethiopianClock = vm.ethiopianClock,
                        onClick = { onOpenTx(tx) }
                    )
                    if (index < latest.size - 1) RowDivider()
                }
            }
        }
    }
}
