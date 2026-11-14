package com.genzebe.app.ui

import com.genzebe.app.core.EthiopianCalendar
import com.genzebe.app.core.EthiopianClock
import com.genzebe.app.core.Money
import com.genzebe.app.core.TxKind
import com.genzebe.app.core.TxLite
import com.genzebe.app.data.TxEntity
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

fun TxEntity.kindEnum(): TxKind = TxKind.valueOf(kind)

fun TxEntity.toLite(): TxLite = TxLite(occurredAt, kindEnum(), totalCents, counterparty ?: memo)

/** True when the app is not sure about this one and the user has not yet accepted it. */
fun TxEntity.needsAttention(): Boolean = !reviewed && (needsReview || gapBefore)

fun TxEntity.title(): String = when {
    kind == TxKind.TRANSFER.name -> if (flow == "IN") "Transfer in, own account" else "Transfer out, own account"
    counterparty != null -> counterparty
    memo != null -> memo
    flow == "IN" -> "Money received"
    else -> "Money sent"
}

fun TxEntity.amountText(): String = when (kindEnum()) {
    TxKind.INCOME -> "+" + Money.format(totalCents)
    TxKind.EXPENSE -> "−" + Money.format(totalCents)
    TxKind.TRANSFER -> Money.format(totalCents)
}

object Fmt {
    fun weekdayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

    fun weekdayShort(index: Int): String =
        java.time.DayOfWeek.of(index + 1).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    /** "ኅዳር 12 · Saturday" */
    fun dayLabel(date: LocalDate): String {
        val e = EthiopianCalendar.fromLocalDate(date)
        return "${EthiopianCalendar.monthName(e.month)} ${e.day} · ${weekdayName(date)}"
    }

    fun time(millis: Long, ethiopianClock: Boolean): String =
        if (ethiopianClock) EthiopianClock.formatMillis(millis) else EthiopianClock.formatStandard(millis)

    /** Short amount for a calendar cell: 1,134 or 12.5k */
    fun compact(cents: Long): String {
        val birr = cents / 100
        return if (birr >= 10_000) String.format(Locale.US, "%.1fk", birr / 1000.0)
        else String.format(Locale.US, "%,d", birr)
    }

    fun percent(value: Double): String = String.format(Locale.US, "%.0f%%", Math.abs(value))
}
