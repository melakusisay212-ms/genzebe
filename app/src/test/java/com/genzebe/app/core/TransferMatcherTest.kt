package com.genzebe.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferMatcherTest {
    private val minute = 60_000L
    private val owners = listOf("Test Owner Person")

    private fun out(id: Long, account: String, birr: Long, at: Long, who: String? = null) =
        MatchCandidate(id, account, MoneyFlow.OUT, birr * 100, at, who, false)

    private fun inc(id: Long, account: String, birr: Long, at: Long, who: String? = null, inst: Boolean = false) =
        MatchCandidate(id, account, MoneyFlow.IN, birr * 100, at, who, inst)

    @Test
    fun sameNameMatchesIgnoringCaseAndExtraWords() {
        assertTrue(TransferMatcher.sameParty("Test Owner", "TEST OWNER PERSON"))
        assertTrue(TransferMatcher.sameParty("test  owner person", "Test Owner Person"))
        assertFalse(TransferMatcher.sameParty("Test Other", "Test Owner Person"))
        // one-word names are too weak to decide
        assertFalse(TransferMatcher.sameParty("Test", "Test Owner Person"))
    }

    @Test
    fun bankToWalletPairWithinMinutesIsATransfer() {
        val t = 1_000_000_000L
        val pairs = TransferMatcher.pairs(
            listOf(
                out(1, "CBE:0761", 65, t),
                inc(2, "TELEBIRR", 65, t + 2 * minute, "Commercial Bank of Ethiopia", inst = true)
            ),
            owners
        )
        assertEquals(listOf(Pair(1L, 2L)), pairs)
    }

    @Test
    fun paymentToAPersonIsNotATransferEvenWithSameAmount() {
        val t = 1_000_000_000L
        val pairs = TransferMatcher.pairs(
            listOf(
                out(1, "CBE:0761", 500, t, "Some Friend"),
                inc(2, "TELEBIRR", 500, t + minute, "Another Sender Name")
            ),
            owners
        )
        assertTrue(pairs.isEmpty())
    }

    @Test
    fun incomingFromOwnNameCanPair() {
        val t = 1_000_000_000L
        val pairs = TransferMatcher.pairs(
            listOf(
                out(1, "CBE:0761", 300, t),
                inc(2, "TELEBIRR", 300, t + minute, "Test Owner Person")
            ),
            owners
        )
        assertEquals(listOf(Pair(1L, 2L)), pairs)
    }

    @Test
    fun tooFarApartOrDifferentAmountDoesNotPair() {
        val t = 1_000_000_000L
        val late = TransferMatcher.pairs(
            listOf(out(1, "A", 65, t), inc(2, "B", 65, t + 30 * minute)),
            owners
        )
        val different = TransferMatcher.pairs(
            listOf(out(1, "A", 65, t), inc(2, "B", 66, t + minute)),
            owners
        )
        assertTrue(late.isEmpty())
        assertTrue(different.isEmpty())
    }

    @Test
    fun sameAccountNeverPairsWithItself() {
        val t = 1_000_000_000L
        val pairs = TransferMatcher.pairs(
            listOf(out(1, "A", 65, t), inc(2, "A", 65, t + minute)),
            owners
        )
        assertTrue(pairs.isEmpty())
    }

    @Test
    fun eachTransactionIsUsedOnce() {
        val t = 1_000_000_000L
        val pairs = TransferMatcher.pairs(
            listOf(
                out(1, "A", 65, t),
                out(2, "A", 65, t + minute),
                inc(3, "B", 65, t + 2 * minute)
            ),
            owners
        )
        assertEquals(1, pairs.size)
        assertEquals(Pair(1L, 3L), pairs[0])
    }

    @Test
    fun ownIncomingIsDetectedFromTheSenderName() {
        val c = inc(1, "TELEBIRR", 64, 0, "Test Owner")
        assertTrue(TransferMatcher.isOwnIncoming(c, owners))
        assertFalse(TransferMatcher.isOwnIncoming(c.copy(counterparty = "Somebody Else"), owners))
        assertFalse(TransferMatcher.isOwnIncoming(c.copy(flow = MoneyFlow.OUT), owners))
    }
}
