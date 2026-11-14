package com.genzebe.app.core

data class MatchCandidate(
    val id: Long,
    val accountKey: String,
    val flow: MoneyFlow,
    val principalCents: Long,
    val timeMillis: Long,
    val counterparty: String?,
    val counterpartyIsInstitution: Boolean
)

/**
 * Decides which transactions are transfers between the user's own accounts.
 *
 * Rule: money leaving one of your accounts and arriving in another of yours is a transfer.
 * Paying a person is an expense. One SMS cannot say who owns the other side, so we use:
 *  1. a matching pair: same amount, different accounts, within [WINDOW_MS], and the incoming
 *     side does not name a different person; or
 *  2. an incoming SMS whose sender name is one of the account holder's own names.
 */
object TransferMatcher {
    const val WINDOW_MS = 15L * 60L * 1000L

    /** True when [name] is the same person as one of the known owner names. */
    fun isOwnName(name: String?, ownerNames: List<String>): Boolean {
        if (name == null) return false
        return ownerNames.any { sameParty(name, it) }
    }

    /**
     * Two names are the same person when both have at least two words and the first two
     * words are equal, ignoring case. "Abe Kebede" matches "Abe Kebede Tesfaye".
     */
    fun sameParty(a: String, b: String): Boolean {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.size < 2 || tb.size < 2) return false
        return ta[0] == tb[0] && ta[1] == tb[1]
    }

    private fun tokens(name: String): List<String> =
        Regex("\\p{L}+").findAll(name.lowercase()).map { it.value }.toList()

    /** Incoming money from the user's own name, with no matching outgoing half needed. */
    fun isOwnIncoming(c: MatchCandidate, ownerNames: List<String>): Boolean =
        c.flow == MoneyFlow.IN && isOwnName(c.counterparty, ownerNames)

    /**
     * Returns pairs of (outgoing id, incoming id). Each transaction is used at most once and
     * the closest in time wins.
     */
    fun pairs(candidates: List<MatchCandidate>, ownerNames: List<String>): List<Pair<Long, Long>> {
        val outs = candidates.filter { it.flow == MoneyFlow.OUT }.sortedBy { it.timeMillis }
        val ins = candidates.filter { it.flow == MoneyFlow.IN }
        val used = HashSet<Long>()
        val result = ArrayList<Pair<Long, Long>>()
        for (out in outs) {
            var best: MatchCandidate? = null
            var bestGap = Long.MAX_VALUE
            for (inc in ins) {
                if (inc.id in used) continue
                if (inc.accountKey == out.accountKey) continue
                if (inc.principalCents != out.principalCents) continue
                val gap = Math.abs(inc.timeMillis - out.timeMillis)
                if (gap > WINDOW_MS) continue
                if (!incomingSideAllowsPair(inc, ownerNames)) continue
                if (gap < bestGap) {
                    best = inc
                    bestGap = gap
                }
            }
            if (best != null) {
                used.add(best.id)
                result.add(Pair(out.id, best.id))
            }
        }
        return result
    }

    /** The incoming side must not name some other person. */
    private fun incomingSideAllowsPair(inc: MatchCandidate, ownerNames: List<String>): Boolean {
        if (inc.counterparty == null) return true
        if (inc.counterpartyIsInstitution) return true
        return isOwnName(inc.counterparty, ownerNames)
    }
}
