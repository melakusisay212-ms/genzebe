package com.genzebe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genzebe.app.core.EthiopianCalendar
import com.genzebe.app.core.Money
import com.genzebe.app.core.Stats
import com.genzebe.app.core.TxKind

/** Week against last week, month to date against last month, who you paid most, and a few plain facts. */
@Composable
fun AnalysisScreen(vm: MainViewModel, nowMillis: Long) {
    val txs by vm.transactions.collectAsState()
    val lite = remember(txs) { txs.map { it.toLite() } }

    val today = EthiopianCalendar.localDateOf(nowMillis)
    val eth = EthiopianCalendar.fromLocalDate(today)
    val (py, pm) = EthiopianCalendar.previousMonth(eth.year, eth.month)

    val week = Stats.weekComparison(lite, TxKind.EXPENSE, today)
    val mtd = Stats.monthToDateComparison(lite, TxKind.EXPENSE, today)
    val thisWeek = Stats.weekDaily(lite, TxKind.EXPENSE, today)
    val lastWeek = Stats.weekDaily(lite, TxKind.EXPENSE, today.minusDays(7))
    val cumThis = Stats.cumulativeMonth(lite, TxKind.EXPENSE, eth.year, eth.month, eth.day)
    val prevDays = EthiopianCalendar.daysInMonth(py, pm)
    val cumPrev = Stats.cumulativeMonth(lite, TxKind.EXPENSE, py, pm, prevDays)
    val payees = Stats.topPayees(lite, eth.year, eth.month, 5)

    val monthExpense = Stats.monthSum(lite, TxKind.EXPENSE, eth.year, eth.month)
    val daysInThis = EthiopianCalendar.daysInMonth(eth.year, eth.month)
    val average = if (eth.day > 0) monthExpense / eth.day else 0L
    val projection = average * daysInThis

    val byDate = Stats.totalsByDate(lite, TxKind.EXPENSE)
    var topDate: java.time.LocalDate? = null
    var topValue = 0L
    for (d in 1..eth.day) {
        val date = EthiopianCalendar.toLocalDate(com.genzebe.app.core.EthiopianDate(eth.year, eth.month, d))
        val v = byDate[date] ?: 0L
        if (v > topValue) {
            topValue = v
            topDate = date
        }
    }
    val fees = txs.filter {
        it.kind == TxKind.EXPENSE.name &&
            EthiopianCalendar.fromMillis(it.occurredAt).let { e -> e.year == eth.year && e.month == eth.month }
    }.sumOf { it.feeCents }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Analysis", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = G.Ink)

        // Week against week
        GCard(padding = 18.dp) {
            Text("This week against last week", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = G.Muted)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Money.format(week.currentCents), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = G.Ink)
                Text("ETB", fontSize = 13.sp, color = G.Muted, modifier = Modifier.padding(bottom = 4.dp))
            }
            DeltaLabel(week, "vs last week")
            Spacer(Modifier.height(14.dp))
            WeekBars(thisWeek, lastWeek)
            Spacer(Modifier.height(8.dp))
            Legend(G.Expense, "This week", G.LastPeriod, "Last week")
        }

        // Month to date
        GCard(padding = 18.dp) {
            Text(
                text = "${EthiopianCalendar.monthName(eth.month)} 1–${eth.day} against ${EthiopianCalendar.monthName(pm)} 1–${minOf(eth.day, prevDays)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = G.Muted
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Money.format(mtd.currentCents), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = G.Ink)
                Text("ETB", fontSize = 13.sp, color = G.Muted, modifier = Modifier.padding(bottom = 4.dp))
            }
            DeltaLabel(mtd, "vs last month")
            Spacer(Modifier.height(14.dp))
            CumulativeLines(cumThis, cumPrev.take(maxOf(eth.day, 1)))
            Spacer(Modifier.height(8.dp))
            Legend(G.Expense, EthiopianCalendar.monthName(eth.month), G.LastPeriod, EthiopianCalendar.monthName(pm))
        }

        // Top payees
        GCard(padding = 18.dp) {
            Text("Paid most this month", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = G.Muted)
            Spacer(Modifier.height(8.dp))
            if (payees.isEmpty()) {
                Text("Nothing yet", fontSize = 14.sp, color = G.Muted)
            } else {
                val max = payees.first().second.coerceAtLeast(1L)
                payees.forEach { (name, cents) ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = G.Ink,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(Money.format(cents), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = G.Expense)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(G.ExpenseBg)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((cents.toFloat() / max.toFloat()).coerceIn(0.03f, 1f))
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(G.Expense)
                            )
                        }
                    }
                }
            }
        }

        // Plain facts
        GCard(padding = 18.dp) {
            Text("This month in numbers", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = G.Muted)
            Spacer(Modifier.height(8.dp))
            FactRow("Average per day", Money.format(average))
            RowDivider()
            FactRow(
                "Busiest day",
                if (topDate == null) "None yet" else Fmt.dayLabel(topDate).substringBefore(" ·") + " · " + Money.format(topValue)
            )
            RowDivider()
            FactRow("If this pace continues", Money.format(projection))
            RowDivider()
            FactRow("Fees paid", Money.format(fees))
        }
    }
}

@Composable
private fun FactRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = G.Muted)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = G.Ink)
    }
}

@Composable
private fun Legend(c1: Color, l1: String, c2: Color, l2: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c1))
            Spacer(Modifier.width(6.dp))
            Text(l1, fontSize = 12.sp, color = G.Muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c2))
            Spacer(Modifier.width(6.dp))
            Text(l2, fontSize = 12.sp, color = G.Muted)
        }
    }
}

/** Seven pairs of bars, Monday first. Last week is grey, this week is red. */
@Composable
private fun WeekBars(thisWeek: List<Long>, lastWeek: List<Long>) {
    val max = maxOf(thisWeek.maxOrNull() ?: 0L, lastWeek.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Column {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / 7f
            val barW = slot * 0.28f
            val base = size.height
            for (i in 0 until 7) {
                val cx = slot * i + slot / 2f
                val h1 = (lastWeek.getOrElse(i) { 0L }.toFloat() / max.toFloat()) * (size.height - 4f)
                val h2 = (thisWeek.getOrElse(i) { 0L }.toFloat() / max.toFloat()) * (size.height - 4f)
                drawLine(G.LastPeriod, Offset(cx - barW * 0.7f, base), Offset(cx - barW * 0.7f, base - maxOf(h1, 2f)), strokeWidth = barW, cap = StrokeCap.Round)
                drawLine(G.Expense, Offset(cx + barW * 0.7f, base), Offset(cx + barW * 0.7f, base - maxOf(h2, 2f)), strokeWidth = barW, cap = StrokeCap.Round)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    text = EthiopianCalendar.WEEKDAY_NAMES_SHORT[i],
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    color = G.Muted
                )
            }
        }
    }
}

/** Running totals for two months drawn on the same scale, so the gap between them is the story. */
@Composable
private fun CumulativeLines(current: List<Long>, previous: List<Long>) {
    val count = maxOf(current.size, previous.size, 2)
    val max = maxOf(current.maxOrNull() ?: 0L, previous.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        fun path(values: List<Long>): Path {
            val p = Path()
            values.forEachIndexed { i, v ->
                val x = size.width * i / (count - 1).toFloat()
                val y = size.height - (v.toFloat() / max.toFloat()) * (size.height - 6f) - 3f
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            return p
        }
        if (previous.isNotEmpty()) {
            drawPath(path(previous), G.LastPeriod, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (current.isNotEmpty()) {
            drawPath(path(current), G.Expense, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
