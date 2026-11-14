package com.genzebe.app.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsTest {

    private fun at(year: Int, month: Int, day: Int, hour: Int = 10): Long =
        EthiopianCalendar.startOfDayMillis(EthiopianCalendar.toLocalDate(EthiopianDate(year, month, day))) +
            hour * 3_600_000L

    private fun expense(ms: Long, birr: Long, label: String? = null) =
        TxLite(ms, TxKind.EXPENSE, birr * 100, label)

    private fun income(ms: Long, birr: Long) = TxLite(ms, TxKind.INCOME, birr * 100)

    private fun transfer(ms: Long, birr: Long) = TxLite(ms, TxKind.TRANSFER, birr * 100)

    // Hidar 12, 2019 is Saturday 21 November 2026
    private val today: LocalDate = EthiopianCalendar.toLocalDate(EthiopianDate(2019, 3, 12))

    @Test
    fun todayFixtureIsASaturday() {
        assertEquals(LocalDate.of(2026, 11, 21), today)
    }

    @Test
    fun monthToDateComparesTheSameDayNumbers() {
        val txs = listOf(
            expense(at(2019, 3, 1), 100),
            expense(at(2019, 3, 12), 50),
            expense(at(2019, 3, 20), 999),      // after today in the test, still same month but not counted to date
            expense(at(2019, 2, 1), 200),
            expense(at(2019, 2, 12), 30),
            expense(at(2019, 2, 13), 777),      // day 13 of the previous month is outside 1..12
            expense(at(2019, 2, 30), 888)
        )
        val c = Stats.monthToDateComparison(txs, TxKind.EXPENSE, today)
        assertEquals(150_00L, c.currentCents)
        assertEquals(230_00L, c.previousCents)
        assertEquals(-80_00L, c.deltaCents)
        assertEquals(-34.78, c.percent!!, 0.01)
    }

    @Test
    fun monthToDateUsesWholePagumeWhenPreviousMonthIsShort() {
        // Today is Meskerem 8, 2019. The previous month is Pagume 2018 with only 5 days.
        val meskerem8 = EthiopianCalendar.toLocalDate(EthiopianDate(2019, 1, 8))
        val txs = listOf(
            expense(at(2019, 1, 2), 10),
            expense(at(2019, 1, 9), 99),         // after today
            expense(at(2018, 13, 1), 20),
            expense(at(2018, 13, 5), 30),
            expense(at(2018, 12, 30), 500)       // Nehase, not part of the comparison
        )
        val c = Stats.monthToDateComparison(txs, TxKind.EXPENSE, meskerem8)
        assertEquals(10_00L, c.currentCents)
        assertEquals(50_00L, c.previousCents)
    }

    @Test
    fun monthToDateAcrossTheYearBoundary() {
        // Meskerem 3, 2019: the previous month is Pagume 2018, the year before
        val meskerem3 = EthiopianCalendar.toLocalDate(EthiopianDate(2019, 1, 3))
        val txs = listOf(
            expense(at(2019, 1, 1), 40),
            expense(at(2018, 13, 3), 25),
            expense(at(2018, 13, 4), 5)           // day 4 is after day 3
        )
        val c = Stats.monthToDateComparison(txs, TxKind.EXPENSE, meskerem3)
        assertEquals(40_00L, c.currentCents)
        assertEquals(25_00L, c.previousCents)
    }

    @Test
    fun weekComparisonRunsMondayToTheSameWeekday() {
        // This week: Mon 16 Nov to Sat 21 Nov 2026. Last week: Mon 9 Nov to Sat 14 Nov.
        fun ms(date: LocalDate) = EthiopianCalendar.startOfDayMillis(date) + 10 * 3_600_000L
        val txs = listOf(
            expense(ms(LocalDate.of(2026, 11, 16)), 10),   // Monday, this week
            expense(ms(LocalDate.of(2026, 11, 21)), 20),   // today
            expense(ms(LocalDate.of(2026, 11, 22)), 500),  // Sunday, after today
            expense(ms(LocalDate.of(2026, 11, 9)), 40),    // Monday, last week
            expense(ms(LocalDate.of(2026, 11, 14)), 60),   // Saturday, last week
            expense(ms(LocalDate.of(2026, 11, 15)), 900)   // Sunday last week, after the same weekday
        )
        val c = Stats.weekComparison(txs, TxKind.EXPENSE, today)
        assertEquals(30_00L, c.currentCents)
        assertEquals(100_00L, c.previousCents)
    }

    @Test
    fun transfersNeverCountAsIncomeOrExpense() {
        val txs = listOf(
            income(at(2019, 3, 5), 1000),
            expense(at(2019, 3, 6), 100),
            transfer(at(2019, 3, 7), 300)
        )
        assertEquals(1000_00L, Stats.monthSum(txs, TxKind.INCOME, 2019, 3))
        assertEquals(100_00L, Stats.monthSum(txs, TxKind.EXPENSE, 2019, 3))
        assertEquals(0L, Stats.monthSum(txs, TxKind.EXPENSE, 2019, 2))
    }

    @Test
    fun monthSumUsesEthiopianMonthEdges() {
        val txs = listOf(
            expense(at(2019, 2, 30, hour = 23), 5),   // last evening of Tikimt
            expense(at(2019, 3, 1, hour = 0), 7),     // first minute of Hidar
            expense(at(2019, 3, 30, hour = 23), 11)   // last evening of Hidar
        )
        assertEquals(18_00L, Stats.monthSum(txs, TxKind.EXPENSE, 2019, 3))
        assertEquals(5_00L, Stats.monthSum(txs, TxKind.EXPENSE, 2019, 2))
    }

    @Test
    fun weekDailyHasSevenValuesMondayFirst() {
        fun ms(date: LocalDate) = EthiopianCalendar.startOfDayMillis(date) + 10 * 3_600_000L
        val txs = listOf(
            expense(ms(LocalDate.of(2026, 11, 16)), 10),
            expense(ms(LocalDate.of(2026, 11, 16)), 5),
            expense(ms(LocalDate.of(2026, 11, 18)), 7)
        )
        val week = Stats.weekDaily(txs, TxKind.EXPENSE, today)
        assertEquals(listOf(15_00L, 0L, 7_00L, 0L, 0L, 0L, 0L), week)
    }

    @Test
    fun cumulativeMonthKeepsARunningTotal() {
        val txs = listOf(
            expense(at(2019, 3, 1), 10),
            expense(at(2019, 3, 3), 5)
        )
        assertEquals(listOf(10_00L, 10_00L, 15_00L, 15_00L), Stats.cumulativeMonth(txs, TxKind.EXPENSE, 2019, 3, 4))
    }

    @Test
    fun topPayeesAreGroupedAndSorted() {
        val txs = listOf(
            expense(at(2019, 3, 2), 100, "Person A"),
            expense(at(2019, 3, 3), 50, "Person B"),
            expense(at(2019, 3, 4), 80, "Person A"),
            expense(at(2019, 2, 4), 999, "Person C"),
            expense(at(2019, 3, 5), 10, null)
        )
        val top = Stats.topPayees(txs, 2019, 3, 5)
        assertEquals(listOf(Pair("Person A", 180_00L), Pair("Person B", 50_00L)), top)
    }

    @Test
    fun percentIsNullWithoutAPreviousPeriod() {
        assertNull(Comparison(100, 0).percent)
        assertEquals(100.0, Comparison(200, 100).percent!!, 0.001)
    }

    @Test
    fun balanceChainFlagsMissingMessages() {
        // real CBE sequence: 48,687.93 then a 5,001.20 transfer leaves 43,686.73
        assertFalse(BalanceChain.hasGap(48_687_93, MoneyFlow.OUT, 5_001_20, 43_686_73))
        // 49,535.71 then a 337.52 debit would leave 49,198.19, but the SMS says 48,687.93
        assertTrue(BalanceChain.hasGap(49_535_71, MoneyFlow.OUT, 337_52, 48_687_93))
        assertFalse(BalanceChain.hasGap(10_00, MoneyFlow.IN, 5_00, 15_00))
        assertFalse(BalanceChain.hasGap(null, MoneyFlow.IN, 5_00, 15_00))
    }
}
