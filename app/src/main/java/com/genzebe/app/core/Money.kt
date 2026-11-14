package com.genzebe.app.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** Money is always stored as a whole number of cents (santim) so sums are exact. */
object Money {
    fun parse(text: String): Long =
        BigDecimal(text.replace(",", "").trim())
            .movePointRight(2)
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()

    /** 123456 -> "1,234.56" */
    fun format(cents: Long): String {
        val abs = Math.abs(cents)
        val text = String.format(Locale.US, "%,d.%02d", abs / 100, abs % 100)
        return if (cents < 0) "-$text" else text
    }

    fun formatSigned(cents: Long): String = when {
        cents > 0 -> "+" + format(cents)
        cents < 0 -> "−" + format(-cents)
        else -> format(0)
    }
}
