package com.genzebe.app.core

import java.time.Instant
import java.time.LocalDateTime

internal const val NUM = "(\\d[\\d,]*(?:\\.\\d+)?)"

/** One bank or wallet. Add a new provider by adding one object that implements this. */
internal interface ProviderParser {
    fun matches(text: String, sender: String?): Boolean
    fun parse(text: String): ParseResult
}

/**
 * Reads one SMS and says what money moved. Pure Kotlin, no Android classes, so it is unit tested.
 *
 * Providers are recognised by their wording, not by sender number, because senders differ
 * between phones. A sender allow-list can be added later in [KnownSenders].
 */
object SmsParser {
    private val providers: List<ProviderParser> = listOf(
        CbeBirrParser, CbeParser, BoaParser, TelebirrParser, MpesaParser, EbirrParser
    )

    private val otp = Regex("(?i)\\b(otp|verification code|one[- ]time (?:pass|pin)|do not share)\\b")
    private val failed = Regex("(?i)\\b(failed|unsuccessful|declined)\\b")

    fun parse(sender: String?, body: String): ParseResult {
        val text = body.replace(' ', ' ').trim()
        if (text.isEmpty()) return ParseResult.Unrecognized
        return try {
            // A message can mention another provider (an M-PESA SMS names "Bank of Abyssinia"),
            // so a provider that matches but cannot parse hands over to the next one.
            for (p in providers) {
                if (!p.matches(text, sender)) continue
                if (otp.containsMatchIn(text) && !text.contains("balance", ignoreCase = true)) {
                    return ParseResult.Ignored("verification code")
                }
                if (failed.containsMatchIn(text)) return ParseResult.Ignored("failed transaction")
                val result = p.parse(text)
                if (result !is ParseResult.Unrecognized) return result
            }
            ParseResult.Unrecognized
        } catch (e: NumberFormatException) {
            ParseResult.Unrecognized
        } catch (e: ArithmeticException) {
            ParseResult.Unrecognized
        }
    }
}

/** Helpers shared by the provider parsers. */
internal object Rx {
    fun first(pattern: String, text: String, ignoreCase: Boolean = true): MatchResult? {
        val rx = if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
        return rx.find(text)
    }

    fun group(pattern: String, text: String, ignoreCase: Boolean = true): String? =
        first(pattern, text, ignoreCase)?.groupValues?.get(1)

    fun cents(pattern: String, text: String): Long? = group(pattern, text)?.let { Money.parse(it) }

    fun name(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw.replace(Regex("\\s+"), " ").trim().trimEnd('.', ',', ':')
        return cleaned.ifEmpty { null }
    }

    fun looksLikeInstitution(name: String): Boolean =
        Regex("(?i)\\b(bank|b2c|c2b|settlement|atm|recycler|telecom|safaricom|ethio|merchant|p2p|cbe|abyssinia)\\b")
            .containsMatchIn(name)

    fun fullNames(vararg names: String?): List<String> =
        names.filterNotNull().map { it.trim() }.filter { it.split(Regex("\\s+")).size >= 2 }

    /** "1********0761" -> "0761" */
    fun maskedTail(pattern: String, text: String): String? {
        val m = first(pattern, text) ?: return null
        return m.groupValues[m.groupValues.size - 1]
    }

    fun instant(
        year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0
    ): Instant? = try {
        LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(EthiopianCalendar.ZONE).toInstant()
    } catch (e: java.time.DateTimeException) {
        null
    }

    fun year4(raw: String): Int {
        val y = raw.toInt()
        return if (raw.length <= 2) 2000 + y else y
    }
}

/** Commercial Bank of Ethiopia, the bank account SMS (amounts in ETB, "Banking with CBE"). */
internal object CbeParser : ProviderParser {
    override fun matches(text: String, sender: String?): Boolean =
        text.contains("Banking with CBE", ignoreCase = true) ||
            text.contains("mbreciept.cbe.com.et", ignoreCase = true)

    override fun parse(text: String): ParseResult {
        val received = Rx.cents("""you have received\s+ETB\s*$NUM""", text)
        val credited = Rx.cents("""has been credited with\s+ETB\s*$NUM""", text)
        val debitedWith = Rx.cents("""has been debited with\s+ETB\s*$NUM""", text)
        val transferred = Rx.cents("""transferred\s+ETB\s*$NUM""", text)
        val debitTx = Rx.cents("""debit transaction of\s+ETB\s*$NUM""", text)

        val flow: MoneyFlow
        var principal: Long
        var fee = 0L
        val serviceCharge = Rx.cents("""Service charge(?: of)?\s*ETB\s*$NUM""", text) ?: 0L
        val vat = Rx.cents("""VAT\(15%\)\s*(?:of\s*)?ETB\s*$NUM""", text) ?: 0L
        val declaredTotal = Rx.cents("""with total of\s+ETB\s*$NUM""", text)

        when {
            received != null -> { flow = MoneyFlow.IN; principal = received }
            credited != null -> { flow = MoneyFlow.IN; principal = credited }
            debitedWith != null -> {
                // "debited with ETB X including Service charge ..." : X is already the full total.
                flow = MoneyFlow.OUT
                fee = serviceCharge + vat
                principal = debitedWith - fee
                if (principal < 0) { principal = debitedWith; fee = 0L }
            }
            transferred != null -> {
                flow = MoneyFlow.OUT
                principal = transferred
                fee = feeFrom(principal, declaredTotal, serviceCharge + vat)
            }
            debitTx != null -> {
                flow = MoneyFlow.OUT
                principal = debitTx
                fee = feeFrom(principal, declaredTotal, serviceCharge + vat)
            }
            else -> return ParseResult.Unrecognized
        }

        val tail = if (flow == MoneyFlow.IN) {
            Rx.maskedTail("""(?:to )?your account\s+(\d)\*+(\d{2,4})""", text)
        } else {
            Rx.maskedTail("""account\s+(\d)\*+(\d{2,4})""", text)
        }
        val key = if (tail != null) "CBE:$tail" else "CBE"
        val label = if (tail != null) "CBE ••$tail" else "CBE"

        val counterparty = Rx.name(
            if (flow == MoneyFlow.IN) {
                Rx.group("""from account\s+\S+\s*\(([^)]+)\)""", text)
            } else {
                Rx.group("""to account\s+\S+\s*\(([^)]+)\)""", text)
            }
        )
        val holder = Rx.name(
            Rx.group("""Dear\s+(?:(?:Mr|Mrs|Ms)\.?\s+)?(.+?)\s+(?:you have|a debit|your account)""", text)
        )

        return ParseResult.Parsed(
            ParsedTransaction(
                provider = Provider.CBE,
                accountKey = key,
                accountLabel = label,
                flow = flow,
                principalCents = principal,
                feeCents = fee,
                balanceAfterCents = Rx.cents("""current balance is\s+ETB\s*$NUM""", text),
                counterparty = counterparty,
                counterpartyIsInstitution = counterparty?.let { Rx.looksLikeInstitution(it) } ?: false,
                txnId = Rx.group("""mbreciept\.cbe\.com\.et/v2-([A-Za-z0-9]+)""", text),
                holderName = holder,
                ownerNames = Rx.fullNames(holder)
            )
        )
    }

    private fun feeFrom(principal: Long, declaredTotal: Long?, parts: Long): Long =
        if (declaredTotal != null && declaredTotal >= principal) declaredTotal - principal else parts
}

/** CBE Birr wallet (amounts as "200.00Br."). */
internal object CbeBirrParser : ProviderParser {
    override fun matches(text: String, sender: String?): Boolean =
        text.contains("CBE Birr", ignoreCase = true)

    override fun parse(text: String): ParseResult {
        if (text.contains("voucher number", ignoreCase = true)) {
            return ParseResult.Ignored("ATM cash-out voucher, no money moved yet")
        }
        if (Rx.first("""you received\s+$NUM\s*Br\.?\s+airtime from""", text) != null) {
            return ParseResult.Ignored("airtime received notice, not an account movement")
        }

        val withdrawn = Rx.cents("""withdrawn\s+$NUM\s*Br""", text)
        val credited = Rx.cents("""credited with\s+$NUM\s*Br""", text)
        val bought = Rx.cents("""you bought\s+$NUM\s*Br""", text)
        val sent = Rx.cents("""you (?:have )?(?:sent|transferred|paid)\s+$NUM\s*Br""", text)
        val received = Rx.cents("""you (?:have )?received\s+$NUM\s*Br""", text)

        val flow: MoneyFlow
        val principal: Long
        var fee = 0L
        var memo: String? = null
        when {
            withdrawn != null -> {
                flow = MoneyFlow.OUT
                principal = withdrawn
                // "You have paid 0.15Br. for 5% Disaster Risk ..." and "Charge 0.70Br."
                val disaster = Rx.cents("""you have paid\s+$NUM\s*Br\.?\s+for""", text) ?: 0L
                val charge = Rx.cents("""Charge\s+$NUM\s*Br""", text) ?: 0L
                fee = disaster + charge
                memo = "ATM withdrawal"
            }
            credited != null -> { flow = MoneyFlow.IN; principal = credited }
            bought != null -> {
                flow = MoneyFlow.OUT
                principal = bought
                // group 1 is the amount, 2 is what was bought, 3 is the number it was bought for
                val m = Rx.first("""you bought\s+$NUM\s*Br\.?\s+of\s+(.+?)\s+for\s+(\d+)""", text)
                memo = if (m != null) Rx.name(m.groupValues[2] + " for " + m.groupValues[3]) else null
            }
            sent != null -> { flow = MoneyFlow.OUT; principal = sent }
            received != null -> { flow = MoneyFlow.IN; principal = received }
            else -> return ParseResult.Unrecognized
        }

        // "on 01/03/26 20:39,Txn ID DC131754IML" - day/month/two-digit year
        val dt = Rx.first("""on\s+(\d{1,2})/(\d{1,2})/(\d{2,4})\s+(\d{1,2}):(\d{2})""", text)
        val occurred = dt?.let {
            Rx.instant(
                Rx.year4(it.groupValues[3]), it.groupValues[2].toInt(), it.groupValues[1].toInt(),
                it.groupValues[4].toInt(), it.groupValues[5].toInt()
            )
        }

        return ParseResult.Parsed(
            ParsedTransaction(
                provider = Provider.CBE_BIRR,
                accountKey = "CBEBIRR",
                accountLabel = "CBE Birr",
                flow = flow,
                principalCents = principal,
                feeCents = fee,
                balanceAfterCents = Rx.cents("""balance is\s+$NUM\s*Br""", text),
                memo = memo,
                txnId = Rx.group("""Txn ID\s+([A-Z0-9]+)""", text, ignoreCase = false),
                occurredAt = occurred,
                holderName = Rx.name(Rx.group("""Dear\s+([A-Za-z]+)\s*,""", text))
            )
        )
    }
}

/** Bank of Abyssinia. The SMS has no date; the received time is used. */
internal object BoaParser : ProviderParser {
    override fun matches(text: String, sender: String?): Boolean =
        text.contains("Bank of Abyssinia", ignoreCase = true) ||
            text.contains("bankofabyssinia.com", ignoreCase = true)

    override fun parse(text: String): ParseResult {
        val m = Rx.first(
            """account\s+(\d)\*+(\d{2,4})\s+was\s+(credited|debited)\s+with\s+ETB\s*$NUM(?:\s+by\s+(.+?))?\.\s+Available Balance:\s*ETB\s*$NUM""",
            text
        ) ?: return ParseResult.Unrecognized

        val tail = m.groupValues[2]
        val flow = if (m.groupValues[3].equals("credited", ignoreCase = true)) MoneyFlow.IN else MoneyFlow.OUT
        val by = Rx.name(m.groupValues[5].ifEmpty { null })
        val atm = by?.contains("ATM", ignoreCase = true) == true

        return ParseResult.Parsed(
            ParsedTransaction(
                provider = Provider.BOA,
                accountKey = "BOA:$tail",
                accountLabel = "BoA ••$tail",
                flow = flow,
                principalCents = Money.parse(m.groupValues[4]),
                balanceAfterCents = Money.parse(m.groupValues[6]),
                counterparty = by,
                counterpartyIsInstitution = by != null && Rx.looksLikeInstitution(by),
                memo = if (atm) "ATM cash deposit" else null,
                txnId = Rx.group("""slip/\?trx=([A-Z0-9]+)""", text, ignoreCase = false),
                holderName = Rx.name(Rx.group("""Dear\s+([A-Za-z ]+?)\s*,""", text))
            )
        )
    }
}

/** telebirr. Three message shapes: package payments, wallet receipts, bank-to-wallet receipts. */
internal object TelebirrParser : ProviderParser {
    override fun matches(text: String, sender: String?): Boolean =
        text.contains("telebirr", ignoreCase = true)

    override fun parse(text: String): ParseResult {
        val paid = Rx.cents("""you have paid\s+ETB\s*$NUM""", text)
        val sent = Rx.cents("""you have (?:transferred|sent)\s+ETB\s*$NUM""", text)
        val received = Rx.cents("""you have received\s+ETB\s*$NUM""", text)

        val flow: MoneyFlow
        val principal: Long
        when {
            paid != null -> { flow = MoneyFlow.OUT; principal = paid }
            sent != null -> { flow = MoneyFlow.OUT; principal = sent }
            received != null -> { flow = MoneyFlow.IN; principal = received }
            else -> return ParseResult.Unrecognized
        }

        val fee = (Rx.cents("""service (?:fee|charge)(?: is)?\s*(?:of\s*)?ETB\s*$NUM""", text) ?: 0L) +
            (Rx.cents("""VAT(?: is)?\s*(?:of\s*)?ETB\s*$NUM""", text) ?: 0L)

        var occurred: Instant? = null
        val dmy = Rx.first("""(\d{2})/(\d{2})/(\d{4})\s+(\d{2}):(\d{2}):(\d{2})""", text)
        val iso = Rx.first("""(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2}):(\d{2})""", text)
        if (dmy != null) {
            val g = dmy.groupValues
            occurred = Rx.instant(g[3].toInt(), g[2].toInt(), g[1].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
        } else if (iso != null) {
            val g = iso.groupValues
            occurred = Rx.instant(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
        }

        var counterparty: String? = null
        var memo: String? = null
        if (flow == MoneyFlow.IN) {
            counterparty = Rx.name(
                Rx.group("""from\s+(.+?)\s*\(\d[\d*]+\)""", text)
                    ?: Rx.group("""from\s+(.+?)\s+to your telebirr account""", text)
            )
        } else {
            memo = Rx.name(Rx.group("""for\s+(.+?)\s+purchase made for""", text))
                ?.removePrefix("package ")?.trim()
            if (memo == null) {
                counterparty = Rx.name(Rx.group("""\bto\s+(.+?)\s*\(\d[\d*]+\)""", text))
            }
        }

        val ownName = Rx.name(Rx.group("""telebirr account\s+\d+\s*-\s*([A-Za-z][A-Za-z ]+?)\.\s""", text))

        return ParseResult.Parsed(
            ParsedTransaction(
                provider = Provider.TELEBIRR,
                accountKey = "TELEBIRR",
                accountLabel = "telebirr",
                flow = flow,
                principalCents = principal,
                feeCents = fee,
                balanceAfterCents = Rx.cents("""current\s+(?:e-money account\s+)?balance is\s+ETB\s*$NUM""", text),
                counterparty = counterparty,
                counterpartyIsInstitution = counterparty?.let { Rx.looksLikeInstitution(it) } ?: false,
                memo = memo,
                txnId = Rx.group("""transaction number\s+(?:is\s+)?([A-Z0-9]{6,})""", text, ignoreCase = false),
                occurredAt = occurred,
                holderName = Rx.name(Rx.group("""Dear\s+([A-Za-z]+)""", text)),
                ownerNames = Rx.fullNames(ownName)
            )
        )
    }
}

/** M-PESA Ethiopia. Amharic messages (amount comes before "ብር") plus a basic English fallback. */
internal object MpesaParser : ProviderParser {
    private const val RECEIVED = "ተቀብለዋል"
    private val outWords = listOf("ገዝተዋል", "ልከዋል", "ከፍለዋል", "አውጥተዋል")

    override fun matches(text: String, sender: String?): Boolean =
        text.contains("M-PESA", ignoreCase = true) || text.contains("MPESA", ignoreCase = true)

    override fun parse(text: String): ParseResult {
        val amharic = text.contains("ብር")
        val flow: MoneyFlow
        val principal: Long
        var counterparty: String? = null
        var memo: String? = null

        if (amharic) {
            val firstAmount = Rx.cents("""$NUM\s*ብር""", text) ?: return ParseResult.Unrecognized
            principal = firstAmount
            when {
                text.contains(RECEIVED) -> {
                    flow = MoneyFlow.IN
                    counterparty = Rx.name(Rx.group("""ከ\s*(.+?)\s+በ\s*\d{1,2}/""", text))
                }
                outWords.any { text.contains(it) } -> {
                    flow = MoneyFlow.OUT
                    memo = Rx.name(Rx.group("""ብር\s*የ?(.+?)\s*ለ\s*(\d+)""", text))
                }
                else -> return ParseResult.Unrecognized
            }
        } else {
            val inAmount = Rx.cents("""you have received\s+ETB\s*$NUM""", text)
            val outAmount = Rx.cents("""you have (?:sent|paid|bought|transferred|withdrawn)\s+ETB\s*$NUM""", text)
            when {
                inAmount != null -> {
                    flow = MoneyFlow.IN; principal = inAmount
                    counterparty = Rx.name(Rx.group("""from\s+(.+?)\s+on\s""", text))
                }
                outAmount != null -> { flow = MoneyFlow.OUT; principal = outAmount }
                else -> return ParseResult.Unrecognized
            }
        }

        val fee = if (amharic) {
            Rx.cents("""የአገልግሎት ክፍያ\s*$NUM\s*ብር""", text) ?: 0L
        } else {
            Rx.cents("""(?:transaction cost|service fee|charge)s?(?: is)?\s*ETB\s*$NUM""", text) ?: 0L
        }

        // "በ 12/9/25 በ 6:20 PM" - day/month/year, then 12-hour time
        var occurred: Instant? = null
        val dt = Rx.first("""(\d{1,2})/(\d{1,2})/(\d{2,4})\s*(?:በ\s*)?(\d{1,2}):(\d{2})\s*(AM|PM)""", text)
        if (dt != null) {
            val g = dt.groupValues
            var hour = g[4].toInt() % 12
            if (g[6].equals("PM", ignoreCase = true)) hour += 12
            occurred = Rx.instant(Rx.year4(g[3]), g[2].toInt(), g[1].toInt(), hour, g[5].toInt())
        }

        val balance = if (amharic) {
            Rx.cents("""ቀሪ ሒሳብ\s*$NUM\s*ብር""", text)
        } else {
            Rx.cents("""balance is\s+ETB\s*$NUM""", text)
        }

        return ParseResult.Parsed(
            ParsedTransaction(
                provider = Provider.MPESA,
                accountKey = "MPESA",
                accountLabel = "M-PESA",
                flow = flow,
                principalCents = principal,
                feeCents = fee,
                balanceAfterCents = balance,
                counterparty = counterparty,
                counterpartyIsInstitution = counterparty?.let { Rx.looksLikeInstitution(it) } ?: false,
                memo = memo,
                txnId = Rx.group("""(?:ቁጥር|number)\s+([A-Z0-9]{6,})""", text, ignoreCase = false),
                occurredAt = occurred,
                holderName = Rx.name(Rx.group("""ውድ\s+(.+?)፣""", text))
            )
        )
    }
}

/** eBirr (Kaafi). */
internal object EbirrParser : ProviderParser {
    override fun matches(text: String, sender: String?): Boolean =
        text.contains("ebirr", ignoreCase = true)

    override fun parse(text: String): ParseResult {
        val sent = Rx.cents("""you sent\s+ETB\s*$NUM""", text)
        val received = Rx.cents("""you received\s+ETB\s*$NUM""", text)
        val flow: MoneyFlow
        val principal: Long
        when {
            sent != null -> { flow = MoneyFlow.OUT; principal = sent }
            received != null -> { flow = MoneyFlow.IN; principal = received }
            else -> return ParseResult.Unrecognized
        }

        // "Charges:ETB1 with VAT:ETB to ..." - the VAT amount can be empty
        val charges = Rx.cents("""Charges:\s*ETB\s*$NUM""", text) ?: 0L
        val vat = Rx.cents("""VAT:\s*ETB\s*$NUM""", text) ?: 0L

        val dt = Rx.first("""at\s+(\d{1,2})/(\d{1,2})/(\d{2,4})\s+(\d{1,2}):(\d{2}):(\d{2})""", text)
        val occurred = dt?.let {
            val g = it.groupValues
            Rx.instant(Rx.year4(g[3]), g[2].toInt(), g[1].toInt(), g[4].toInt(), g[5].toInt(), g[6].toInt())
        }

        val counterparty = Rx.name(Rx.group("""\b(?:to|from)\s+(.+?)\s*\(\d+\)""", text))

        return ParseResult.Parsed(
            ParsedTransaction(
                provider = Provider.EBIRR,
                accountKey = "EBIRR",
                accountLabel = "eBirr",
                flow = flow,
                principalCents = principal,
                feeCents = charges + vat,
                balanceAfterCents = Rx.cents("""Balance is\s+ETB\s*$NUM""", text),
                counterparty = counterparty,
                txnId = Rx.group("""Id:\s*(\d+)""", text),
                occurredAt = occurred
            )
        )
    }
}

/**
 * Placeholder for a sender allow-list. SMS are matched by wording today; once the real
 * sender IDs on your phone are known they can be listed here to tighten the filter.
 */
object KnownSenders {
    val ids: Set<String> = emptySet()
    fun isKnown(sender: String?): Boolean = ids.isEmpty() || (sender != null && ids.any { sender.contains(it, true) })
}
