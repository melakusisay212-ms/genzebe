package com.genzebe.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EthiopianCalendarTest {

    private fun eth(y: Int, m: Int, d: Int) = EthiopianDate(y, m, d)

    @Test
    fun knownDatesConvertCorrectly() {
        val cases = listOf(
            // New year (Enkutatash) and the leap-year shift
            LocalDate.of(2026, 9, 11) to eth(2019, 1, 1),
            LocalDate.of(2027, 9, 12) to eth(2020, 1, 1),
            LocalDate.of(2023, 9, 12) to eth(2016, 1, 1),
            LocalDate.of(2024, 9, 11) to eth(2017, 1, 1),
            // Pagume, 5 days normally and 6 in a leap year
            LocalDate.of(2026, 9, 10) to eth(2018, 13, 5),
            LocalDate.of(2027, 9, 11) to eth(2019, 13, 6),
            LocalDate.of(2023, 9, 11) to eth(2015, 13, 6),
            // Public holidays
            LocalDate.of(2026, 9, 27) to eth(2019, 1, 17),  // Meskel
            LocalDate.of(2026, 1, 7) to eth(2018, 4, 29),   // Genna
            LocalDate.of(2026, 1, 19) to eth(2018, 5, 11),  // Timkat
            LocalDate.of(2026, 3, 2) to eth(2018, 6, 23),   // Adwa victory day
            // Dates used while designing the app
            LocalDate.of(2026, 10, 19) to eth(2019, 2, 9),
            LocalDate.of(2026, 11, 10) to eth(2019, 3, 1),
            LocalDate.of(2026, 11, 12) to eth(2019, 3, 3),
            LocalDate.of(2000, 1, 1) to eth(1992, 4, 22)
        )
        for ((greg, expected) in cases) {
            assertEquals("to Ethiopian $greg", expected, EthiopianCalendar.fromLocalDate(greg))
            assertEquals("to Gregorian $expected", greg, EthiopianCalendar.toLocalDate(expected))
        }
    }

    @Test
    fun roundTripWorksForEveryDayFrom1950To2100() {
        var d = LocalDate.of(1950, 1, 1)
        val end = LocalDate.of(2100, 1, 1)
        while (d.isBefore(end)) {
            val e = EthiopianCalendar.fromLocalDate(d)
            assertEquals(d, EthiopianCalendar.toLocalDate(e))
            d = d.plusDays(1)
        }
    }

    @Test
    fun consecutiveDaysAdvanceOneAtATime() {
        var d = LocalDate.of(2024, 1, 1)
        var prev = EthiopianCalendar.fromLocalDate(d)
        repeat(1500) {
            d = d.plusDays(1)
            val cur = EthiopianCalendar.fromLocalDate(d)
            assertTrue("$prev should be before $cur", prev < cur)
            val sameMonth = cur.year == prev.year && cur.month == prev.month
            if (sameMonth) {
                assertEquals(prev.day + 1, cur.day)
            } else {
                assertEquals(1, cur.day)
                assertEquals(EthiopianCalendar.daysInMonth(prev.year, prev.month), prev.day)
            }
            prev = cur
        }
    }

    @Test
    fun pagumeLengthFollowsLeapYears() {
        assertEquals(5, EthiopianCalendar.daysInMonth(2018, 13))
        assertEquals(6, EthiopianCalendar.daysInMonth(2019, 13))
        assertEquals(6, EthiopianCalendar.daysInMonth(2015, 13))
        assertEquals(5, EthiopianCalendar.daysInMonth(2016, 13))
        assertEquals(30, EthiopianCalendar.daysInMonth(2019, 3))
        assertTrue(EthiopianCalendar.isLeapYear(2019))
        assertFalse(EthiopianCalendar.isLeapYear(2020))
    }

    @Test
    fun invalidDatesAreRejected() {
        try {
            EthiopianDate(2018, 13, 6)
            fail("Pagume 6 does not exist in 2018")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            EthiopianDate(2019, 14, 1)
            fail("month 14 does not exist")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun midnightInAddisAbabaStartsTheNewEthiopianDay() {
        // 2026-11-10 00:00 in Addis Ababa is 2026-11-09 21:00 UTC
        val firstSecond = Instant.parse("2026-11-09T21:00:00Z").toEpochMilli()
        val lastSecondBefore = Instant.parse("2026-11-09T20:59:59Z").toEpochMilli()
        assertEquals(eth(2019, 3, 1), EthiopianCalendar.fromMillis(firstSecond))
        assertEquals(eth(2019, 2, 30), EthiopianCalendar.fromMillis(lastSecondBefore))
    }

    @Test
    fun startOfMonthMillisIsMidnightOfDayOne() {
        val expected = LocalDate.of(2026, 11, 10).atStartOfDay(EthiopianCalendar.ZONE).toInstant().toEpochMilli()
        assertEquals(expected, EthiopianCalendar.startOfMonthMillis(2019, 3))
    }

    @Test
    fun monthGridIsMondayFirst() {
        // Hidar 1, 2019 is Tuesday 10 November 2026, so one empty cell comes first
        val grid = EthiopianCalendar.monthGrid(2019, 3)
        assertEquals(31, grid.size)
        assertNull(grid[0])
        assertEquals(eth(2019, 3, 1), grid[1])
        assertEquals(eth(2019, 3, 30), grid[30])

        // Meskerem 1, 2019 is Friday 11 September 2026: four empty cells
        val meskerem = EthiopianCalendar.monthGrid(2019, 1)
        assertEquals(4, meskerem.takeWhile { it == null }.size)

        // Pagume 2019 has 6 days
        assertEquals(6, EthiopianCalendar.monthGrid(2019, 13).filterNotNull().size)
    }

    @Test
    fun weeksStartOnMonday() {
        // Thursday 12 November 2026
        assertEquals(LocalDate.of(2026, 11, 9), EthiopianCalendar.weekStart(LocalDate.of(2026, 11, 12)))
        // Monday stays itself, Sunday goes back six days
        assertEquals(LocalDate.of(2026, 11, 9), EthiopianCalendar.weekStart(LocalDate.of(2026, 11, 9)))
        assertEquals(LocalDate.of(2026, 11, 9), EthiopianCalendar.weekStart(LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun monthNavigationWrapsAcrossYears() {
        assertEquals(Pair(2018, 13), EthiopianCalendar.previousMonth(2019, 1))
        assertEquals(Pair(2019, 1), EthiopianCalendar.nextMonth(2018, 13))
        assertEquals(Pair(2019, 2), EthiopianCalendar.previousMonth(2019, 3))
        assertEquals(Pair(2019, 4), EthiopianCalendar.nextMonth(2019, 3))
    }

    @Test
    fun formatting() {
        assertEquals("ኅዳር 3, 2019", EthiopianCalendar.format(eth(2019, 3, 3)))
        assertEquals("ጳጉሜን 6, 2019", EthiopianCalendar.format(eth(2019, 13, 6)))
        assertEquals("ኅዳር 2019", EthiopianCalendar.monthLabel(2019, 3))
    }

    @Test
    fun ethiopianClockCountsFromSunrise() {
        assertEquals("12:00 ጠዋት", EthiopianClock.format(LocalTime.of(6, 0)))
        assertEquals("1:00 ጠዋት", EthiopianClock.format(LocalTime.of(7, 0)))
        assertEquals("6:00 ከሰዓት", EthiopianClock.format(LocalTime.of(12, 0)))
        assertEquals("12:55 ማታ", EthiopianClock.format(LocalTime.of(18, 55)))
        assertEquals("2:52 ማታ", EthiopianClock.format(LocalTime.of(20, 52)))
        assertEquals("3:00 ሌሊት", EthiopianClock.format(LocalTime.of(21, 0)))
        assertEquals("6:30 ሌሊት", EthiopianClock.format(LocalTime.of(0, 30)))
    }
}
