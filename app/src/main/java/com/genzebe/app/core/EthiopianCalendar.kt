package com.genzebe.app.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * A date in the Ethiopian (Habesha) calendar.
 * Twelve months of 30 days, then Pagume (month 13) with 5 days, or 6 in a leap year.
 */
data class EthiopianDate(val year: Int, val month: Int, val day: Int) : Comparable<EthiopianDate> {
    init {
        require(month in 1..13) { "month must be 1..13, was $month" }
        require(day in 1..EthiopianCalendar.daysInMonth(year, month)) {
            "day $day is not valid in month $month of $year"
        }
    }

    override fun compareTo(other: EthiopianDate): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return day.compareTo(other.day)
    }
}

/**
 * The single place where Gregorian and Ethiopian dates are converted.
 * Everything else in the app goes through here.
 *
 * The conversion uses Julian Day Numbers. Ethiopian leap years are the years where
 * (year % 4 == 3); the extra day is Pagume 6. Meskerem 1 of 2019 is 11 September 2026.
 */
object EthiopianCalendar {
    /** All SMS times and "today" are read in Addis Ababa time (UTC+3, no daylight saving). */
    val ZONE: ZoneId = ZoneId.of("Africa/Addis_Ababa")

    private const val JDN_OFFSET = 1723856L
    private const val UNIX_EPOCH_JDN = 2440588L

    val MONTH_NAMES = arrayOf(
        "መስከረም", "ጥቅምት", "ኅዳር", "ታኅሣሥ", "ጥር", "የካቲት", "መጋቢት",
        "ሚያዝያ", "ግንቦት", "ሰኔ", "ሐምሌ", "ነሐሴ", "ጳጉሜን"
    )

    val MONTH_NAMES_LATIN = arrayOf(
        "Meskerem", "Tikimt", "Hidar", "Tahsas", "Tir", "Yekatit", "Megabit",
        "Miazia", "Ginbot", "Sene", "Hamle", "Nehase", "Pagume"
    )

    /** Monday first, matching java.time.DayOfWeek order. */
    val WEEKDAY_NAMES_SHORT = arrayOf("ሰኞ", "ማክ", "ረቡ", "ሐሙ", "ዓርብ", "ቅዳ", "እሑ")

    fun isLeapYear(year: Int): Boolean = Math.floorMod(year, 4) == 3

    fun daysInMonth(year: Int, month: Int): Int =
        if (month < 13) 30 else if (isLeapYear(year)) 6 else 5

    fun monthName(month: Int): String = MONTH_NAMES[month - 1]

    fun toJdn(date: EthiopianDate): Long =
        JDN_OFFSET + 365L * date.year + Math.floorDiv(date.year, 4) + 30L * date.month + date.day - 31

    fun fromJdn(jdn: Long): EthiopianDate {
        val days = jdn - JDN_OFFSET
        val cycle = Math.floorDiv(days, 1461L)
        val r = Math.floorMod(days, 1461L)
        val n = r % 365 + 365 * (r / 1460)
        val year = (4 * cycle + r / 365 - r / 1460).toInt()
        val month = (n / 30 + 1).toInt()
        val day = (n % 30 + 1).toInt()
        return EthiopianDate(year, month, day)
    }

    fun fromLocalDate(date: LocalDate): EthiopianDate = fromJdn(date.toEpochDay() + UNIX_EPOCH_JDN)

    fun toLocalDate(date: EthiopianDate): LocalDate = LocalDate.ofEpochDay(toJdn(date) - UNIX_EPOCH_JDN)

    fun localDateOf(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZONE).toLocalDate()

    fun fromMillis(millis: Long): EthiopianDate = fromLocalDate(localDateOf(millis))

    fun startOfDayMillis(date: LocalDate): Long = date.atStartOfDay(ZONE).toInstant().toEpochMilli()

    /** First instant of the given Ethiopian month, in epoch milliseconds. */
    fun startOfMonthMillis(year: Int, month: Int): Long =
        startOfDayMillis(toLocalDate(EthiopianDate(year, month, 1)))

    fun today(nowMillis: Long = System.currentTimeMillis()): EthiopianDate = fromMillis(nowMillis)

    fun previousMonth(year: Int, month: Int): Pair<Int, Int> =
        if (month == 1) Pair(year - 1, 13) else Pair(year, month - 1)

    fun nextMonth(year: Int, month: Int): Pair<Int, Int> =
        if (month == 13) Pair(year + 1, 1) else Pair(year, month + 1)

    /** Monday of the week containing the given date. Weeks start on Monday. */
    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

    /**
     * Cells for a Monday-first month grid: leading nulls for the empty cells before day 1,
     * then every day of the month.
     */
    fun monthGrid(year: Int, month: Int): List<EthiopianDate?> {
        val first = toLocalDate(EthiopianDate(year, month, 1))
        val offset = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells = ArrayList<EthiopianDate?>()
        for (i in 0 until offset) cells.add(null)
        for (d in 1..daysInMonth(year, month)) cells.add(EthiopianDate(year, month, d))
        return cells
    }

    fun format(date: EthiopianDate): String = "${monthName(date.month)} ${date.day}, ${date.year}"

    fun formatShort(date: EthiopianDate): String = "${monthName(date.month)} ${date.day}"

    fun monthLabel(year: Int, month: Int): String = "${monthName(month)} $year"
}

/**
 * Ethiopian clock: the day starts at sunrise, so 06:00 on the standard clock is 12:00
 * and 07:00 is 1:00. This converts a standard time to the Ethiopian reading.
 */
object EthiopianClock {
    fun hour12(hour24: Int): Int {
        val h = (hour24 + 6) % 12
        return if (h == 0) 12 else h
    }

    fun period(hour24: Int): String = when (hour24) {
        in 6..11 -> "ጠዋት"
        in 12..17 -> "ከሰዓት"
        in 18..20 -> "ማታ"
        else -> "ሌሊት"
    }

    fun format(time: LocalTime): String =
        "${hour12(time.hour)}:${time.minute.toString().padStart(2, '0')} ${period(time.hour)}"

    fun formatMillis(millis: Long): String =
        format(Instant.ofEpochMilli(millis).atZone(EthiopianCalendar.ZONE).toLocalTime())

    fun formatStandard(millis: Long): String {
        val t = Instant.ofEpochMilli(millis).atZone(EthiopianCalendar.ZONE).toLocalTime()
        val h12 = if (t.hour % 12 == 0) 12 else t.hour % 12
        val suffix = if (t.hour < 12) "AM" else "PM"
        return "$h12:${t.minute.toString().padStart(2, '0')} $suffix"
    }
}
