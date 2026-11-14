package com.genzebe.app.core

import java.time.LocalDate

/**
 * A transaction reduced to what the totals need.
 * [amountCents] is the total paid for an expense (fee included) or the amount received for income.
 */
data class TxLite(
    val timeMillis: Long,
    val kind: TxKind,
    val amountCents: Long,
    val label: String? = null
)

data class Comparison(val currentCents: Long, val previousCents: Long) {
    val deltaCents: Long get() = currentCents - previousCents

    /** Percent change against the previous period, or null when there is nothing to compare with. */
    val percent: Double?
        get() = if (previousCents == 0L) null else deltaCents * 100.0 / previousCents
}

/** Checks that two consecutive SMS of one account agree on the balance. */
object BalanceChain {
    /**
     * True when a message is probably missing between the previous balance and this one.
     * Unknown balances never raise a flag.
     */
    fun hasGap(prevBalance: Long?, flow: MoneyFlow, totalCents: Long, balanceAfter: Long?): Boolean {
        if (prevBalance == null || balanceAfter == null) return false
        val expected = if (flow == MoneyFlow.IN) prevBalance + totalCents else prevBalance - totalCents
        return Math.abs(expected - balanceAfter) > 1L
    }
}

/**
 * Totals across all accounts. Transfers between the user's own accounts are never counted
 * as income or expense.
 */
object Stats {
    private fun dateOf(tx: TxLite): LocalDate = EthiopianCalendar.localDateOf(tx.timeMillis)

    fun sum(txs: List<TxLite>, kind: TxKind, from: LocalDate, toInclusive: LocalDate): Long {
        var total = 0L
        for (tx in txs) {
            if (tx.kind != kind) continue
            val d = dateOf(tx)
            if (d.isBefore(from) || d.isAfter(toInclusive)) continue
            total += tx.amountCents
        }
        return total
    }

    fun totalsByDate(txs: List<TxLite>, kind: TxKind): Map<LocalDate, Long> {
        val map = HashMap<LocalDate, Long>()
        for (tx in txs) {
            if (tx.kind != kind) continue
            val d = dateOf(tx)
            map[d] = (map[d] ?: 0L) + tx.amountCents
        }
        return map
    }

    fun monthSum(txs: List<TxLite>, kind: TxKind, year: Int, month: Int): Long {
        val first = EthiopianCalendar.toLocalDate(EthiopianDate(year, month, 1))
        val last = EthiopianCalendar.toLocalDate(
            EthiopianDate(year, month, EthiopianCalendar.daysInMonth(year, month))
        )
        return sum(txs, kind, first, last)
    }

    /** Monday to today of this week, against Monday to the same weekday of last week. */
    fun weekComparison(txs: List<TxLite>, kind: TxKind, today: LocalDate): Comparison {
        val start = EthiopianCalendar.weekStart(today)
        val current = sum(txs, kind, start, today)
        val previous = sum(txs, kind, start.minusDays(7), today.minusDays(7))
        return Comparison(current, previous)
    }

    /**
     * Day 1 to today of this Ethiopian month, against day 1 to the same day number of the
     * previous month. When the previous month is shorter (Pagume has 5 or 6 days) the whole
     * previous month is used.
     */
    fun monthToDateComparison(txs: List<TxLite>, kind: TxKind, today: LocalDate): Comparison {
        val eth = EthiopianCalendar.fromLocalDate(today)
        val (py, pm) = EthiopianCalendar.previousMonth(eth.year, eth.month)
        val prevLastDay = minOf(eth.day, EthiopianCalendar.daysInMonth(py, pm))
        val curFirst = EthiopianCalendar.toLocalDate(EthiopianDate(eth.year, eth.month, 1))
        val prevFirst = EthiopianCalendar.toLocalDate(EthiopianDate(py, pm, 1))
        val prevLast = EthiopianCalendar.toLocalDate(EthiopianDate(py, pm, prevLastDay))
        return Comparison(sum(txs, kind, curFirst, today), sum(txs, kind, prevFirst, prevLast))
    }

    /** Seven values, Monday first, for the week that contains [anyDayInWeek]. */
    fun weekDaily(txs: List<TxLite>, kind: TxKind, anyDayInWeek: LocalDate): List<Long> {
        val start = EthiopianCalendar.weekStart(anyDayInWeek)
        val byDate = totalsByDate(txs, kind)
        return (0 until 7).map { byDate[start.plusDays(it.toLong())] ?: 0L }
    }

    /** Running total for days 1..[uptoDay] of an Ethiopian month. */
    fun cumulativeMonth(txs: List<TxLite>, kind: TxKind, year: Int, month: Int, uptoDay: Int): List<Long> {
        val byDate = totalsByDate(txs, kind)
        val result = ArrayList<Long>()
        var running = 0L
        val last = minOf(uptoDay, EthiopianCalendar.daysInMonth(year, month))
        for (d in 1..last) {
            val date = EthiopianCalendar.toLocalDate(EthiopianDate(year, month, d))
            running += byDate[date] ?: 0L
            result.add(running)
        }
        return result
    }

    /** Who you paid most in the given Ethiopian month, largest first. */
    fun topPayees(txs: List<TxLite>, year: Int, month: Int, limit: Int): List<Pair<String, Long>> {
        val first = EthiopianCalendar.toLocalDate(EthiopianDate(year, month, 1))
        val last = EthiopianCalendar.toLocalDate(
            EthiopianDate(year, month, EthiopianCalendar.daysInMonth(year, month))
        )
        val totals = HashMap<String, Long>()
        for (tx in txs) {
            if (tx.kind != TxKind.EXPENSE) continue
            val label = tx.label ?: continue
            val d = dateOf(tx)
            if (d.isBefore(first) || d.isAfter(last)) continue
            totals[label] = (totals[label] ?: 0L) + tx.amountCents
        }
        return totals.entries.sortedByDescending { it.value }.take(limit).map { Pair(it.key, it.value) }
    }
}
