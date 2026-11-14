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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genzebe.app.core.EthiopianCalendar
import com.genzebe.app.core.EthiopianDate
import com.genzebe.app.core.Money
import com.genzebe.app.core.Stats
import com.genzebe.app.core.TxKind
import com.genzebe.app.data.TxEntity

/** Ethiopian month grid, Monday first. Each day is shaded by how much was spent. */
@Composable
fun CalendarScreen(vm: MainViewModel, nowMillis: Long, onOpenTx: (TxEntity) -> Unit) {
    val txs by vm.transactions.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val labels = remember(accounts) { accounts.associate { it.accountKey to it.label } }
    val lite = remember(txs) { txs.map { it.toLite() } }

    val today = EthiopianCalendar.localDateOf(nowMillis)
    val todayEth = EthiopianCalendar.fromLocalDate(today)

    var year by rememberSaveable { mutableStateOf(todayEth.year) }
    var month by rememberSaveable { mutableStateOf(todayEth.month) }
    var selectedDay by rememberSaveable { mutableStateOf(todayEth.day) }

    val daysInMonth = EthiopianCalendar.daysInMonth(year, month)
    if (selectedDay > daysInMonth) selectedDay = daysInMonth

    val expenseByDate = remember(lite) { Stats.totalsByDate(lite, TxKind.EXPENSE) }
    val incomeByDate = remember(lite) { Stats.totalsByDate(lite, TxKind.INCOME) }

    val cells = EthiopianCalendar.monthGrid(year, month)
    val rows = cells.chunked(7).map { row ->
        if (row.size < 7) row + List<EthiopianDate?>(7 - row.size) { null } else row
    }

    var maxSpend = 0L
    for (d in 1..daysInMonth) {
        val date = EthiopianCalendar.toLocalDate(EthiopianDate(year, month, d))
        val v = expenseByDate[date] ?: 0L
        if (v > maxSpend) maxSpend = v
    }

    val monthExpense = Stats.monthSum(lite, TxKind.EXPENSE, year, month)
    val monthIncome = Stats.monthSum(lite, TxKind.INCOME, year, month)
    val isCurrentMonth = year == todayEth.year && month == todayEth.month
    val elapsed = if (isCurrentMonth) todayEth.day else daysInMonth
    val perDay = if (elapsed > 0) monthExpense / elapsed else 0L

    val selectedEth = EthiopianDate(year, month, selectedDay)
    val selectedDate = EthiopianCalendar.toLocalDate(selectedEth)
    val dayTxs = txs.filter { EthiopianCalendar.localDateOf(it.occurredAt) == selectedDate }
    val dayExpense = dayTxs.filter { it.kind == TxKind.EXPENSE.name }.sumOf { it.totalCents }
    val dayIncome = dayTxs.filter { it.kind == TxKind.INCOME.name }.sumOf { it.totalCents }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Month header with previous and next
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NavCircle(left = true) {
                val (py, pm) = EthiopianCalendar.previousMonth(year, month)
                year = py
                month = pm
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = EthiopianCalendar.monthLabel(year, month),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = G.Ink
                )
                val first = EthiopianCalendar.toLocalDate(EthiopianDate(year, month, 1))
                val last = EthiopianCalendar.toLocalDate(EthiopianDate(year, month, daysInMonth))
                Text(
                    text = "${first.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${first.dayOfMonth}" +
                        " – " +
                        "${last.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }} ${last.dayOfMonth}, ${last.year}",
                    fontSize = 12.sp,
                    color = G.Muted
                )
            }
            NavCircle(left = false) {
                val (ny, nm) = EthiopianCalendar.nextMonth(year, month)
                year = ny
                month = nm
            }
        }

        // Month totals
        GCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Spent", fontSize = 12.sp, color = G.Muted)
                    Text(Fmt.compact(monthExpense), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = G.Expense)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Income", fontSize = 12.sp, color = G.Muted)
                    Text(Fmt.compact(monthIncome), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = G.Income)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Avg per day", fontSize = 12.sp, color = G.Muted)
                    Text(Fmt.compact(perDay), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = G.Ink)
                }
            }
        }

        // Weekday header, Monday first
        Row(modifier = Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    text = EthiopianCalendar.WEEKDAY_NAMES_SHORT[i],
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = G.Muted
                )
            }
        }

        // Grid
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (cell in row) {
                        if (cell == null) {
                            Box(Modifier.weight(1f).height(52.dp))
                        } else {
                            val date = EthiopianCalendar.toLocalDate(cell)
                            val spent = expenseByDate[date] ?: 0L
                            val earned = incomeByDate[date] ?: 0L
                            val level = heatLevel(spent, maxSpend)
                            val isToday = date == today
                            val isSelected = cell.day == selectedDay
                            DayCell(
                                modifier = Modifier.weight(1f),
                                day = cell.day,
                                spentText = if (spent > 0) Fmt.compact(spent) else null,
                                level = level,
                                hasIncome = earned > 0,
                                isToday = isToday,
                                isSelected = isSelected,
                                onClick = { selectedDay = cell.day }
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Less", fontSize = 11.sp, color = G.Muted)
                Spacer(Modifier.width(6.dp))
                for (c in G.Heat) {
                    Box(Modifier.padding(end = 4.dp).size(width = 14.dp, height = 10.dp).clip(RoundedCornerShape(3.dp)).background(c))
                }
                Text("More spent", fontSize = 11.sp, color = G.Muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(G.Income))
                Spacer(Modifier.width(6.dp))
                Text("Income day", fontSize = 11.sp, color = G.Muted)
            }
        }

        // Selected day
        GCard(padding = 16.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = Fmt.dayLabel(selectedDate),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = G.Ink
                )
                if (dayExpense > 0) Pill("−" + Money.format(dayExpense), G.ExpenseBg, G.Expense)
                if (dayIncome > 0) Pill("+" + Money.format(dayIncome), G.IncomeBg, G.Income)
            }
            if (dayTxs.isEmpty()) {
                Text(
                    text = "No transactions on this day",
                    fontSize = 14.sp,
                    color = G.Muted,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                )
            } else {
                Spacer(Modifier.height(4.dp))
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

/** 0 for no spending, then 1 to 4 from light to dark relative to the busiest day. */
private fun heatLevel(value: Long, max: Long): Int {
    if (value <= 0L || max <= 0L) return 0
    val ratio = value.toDouble() / max.toDouble()
    return when {
        ratio <= 0.25 -> 1
        ratio <= 0.5 -> 2
        ratio <= 0.75 -> 3
        else -> 4
    }
}

@Composable
private fun NavCircle(left: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(G.Surface)
            .border(1.dp, G.Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (left) Icons.Filled.KeyboardArrowLeft else Icons.Filled.KeyboardArrowRight,
            contentDescription = if (left) "Previous month" else "Next month",
            tint = G.Ink
        )
    }
}

@Composable
private fun DayCell(
    modifier: Modifier,
    day: Int,
    spentText: String?,
    level: Int,
    hasIncome: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val bg = if (level == 0) G.Surface else G.Heat[level - 1]
    val borderColor = when {
        isSelected -> G.Ink
        isToday -> G.Accent
        level == 0 -> G.Border
        else -> bg
    }
    val borderWidth = if (isSelected || isToday) 2.dp else 1.dp
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(bg)
            .border(borderWidth, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = day.toString(),
                fontSize = 13.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = G.Ink
            )
            if (spentText != null) {
                Text(text = spentText, fontSize = 10.sp, color = G.HeatText)
            }
        }
        if (hasIncome) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(G.Income)
            )
        }
    }
}
