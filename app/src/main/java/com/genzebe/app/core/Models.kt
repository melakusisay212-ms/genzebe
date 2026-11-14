package com.genzebe.app.core

import java.time.Instant

enum class Provider(val label: String) {
    CBE("CBE"),
    CBE_BIRR("CBE Birr"),
    BOA("BoA"),
    TELEBIRR("telebirr"),
    MPESA("M-PESA"),
    EBIRR("eBirr")
}

/** Direction of money relative to the account that received the SMS. */
enum class MoneyFlow { IN, OUT }

/** What the user sees: income, expense, or a transfer between their own accounts. */
enum class TxKind { INCOME, EXPENSE, TRANSFER }

/**
 * One transaction read from one SMS.
 *
 * [principalCents] is the amount that was sent or received. [feeCents] is the extra charge
 * paid on top (service charge, VAT, disaster fund). An expense is principal plus fee, as one
 * record, never two. [balanceAfterCents] is the account balance printed in the SMS.
 */
data class ParsedTransaction(
    val provider: Provider,
    val accountKey: String,
    val accountLabel: String,
    val flow: MoneyFlow,
    val principalCents: Long,
    val feeCents: Long = 0L,
    val balanceAfterCents: Long? = null,
    val counterparty: String? = null,
    val counterpartyIsInstitution: Boolean = false,
    val memo: String? = null,
    val txnId: String? = null,
    /** Date and time printed in the SMS, when it has one. Null means use the SMS received time. */
    val occurredAt: Instant? = null,
    val holderName: String? = null,
    /** Full names of the account holder found in this SMS, used to recognise own-account transfers. */
    val ownerNames: List<String> = emptyList(),
    val needsReview: Boolean = false,
    val reviewReason: String? = null
) {
    /** What actually left or entered the account. */
    val totalCents: Long get() = if (flow == MoneyFlow.OUT) principalCents + feeCents else principalCents
}

sealed class ParseResult {
    data class Parsed(val tx: ParsedTransaction) : ParseResult()

    /** Recognised, but deliberately not a transaction (voucher, OTP, failed payment). */
    data class Ignored(val reason: String) : ParseResult()

    /** Not a message this parser understands. */
    object Unrecognized : ParseResult()
}
