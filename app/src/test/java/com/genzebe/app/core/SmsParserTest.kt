package com.genzebe.app.core

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are real message shapes with names, phone numbers and account numbers replaced.
 */
class SmsParserTest {

    private fun parsed(body: String, sender: String? = null): ParsedTransaction {
        val r = SmsParser.parse(sender, body)
        assertTrue("expected Parsed but was $r", r is ParseResult.Parsed)
        return (r as ParseResult.Parsed).tx
    }

    private fun local(i: Instant?): String? = i?.atZone(EthiopianCalendar.ZONE)?.toLocalDateTime()?.toString()

    // ---------------------------------------------------------------- telebirr

    @Test
    fun telebirrPackagePayment() {
        val tx = parsed(
            "Dear TESTER\nYou have paid ETB 34.00 for package Monthly student pack 234 Min + 120SMS plus 234 Min night bonus purchase made for 911000001 on 19/10/2026 18:55:30. Your transaction number is  DJJ32IFLQV. Your current balance is ETB 97.00.To download your payment information please click this link: https://transactioninfo.ethiotelecom.et/receipt/DJJ32IFLQV\nThank you for using telebirr\nEthio telecom"
        )
        assertEquals(Provider.TELEBIRR, tx.provider)
        assertEquals("TELEBIRR", tx.accountKey)
        assertEquals(MoneyFlow.OUT, tx.flow)
        assertEquals(34_00L, tx.principalCents)
        assertEquals(0L, tx.feeCents)
        assertEquals(97_00L, tx.balanceAfterCents)
        assertEquals("DJJ32IFLQV", tx.txnId)
        assertEquals("2026-10-19T18:55:30", local(tx.occurredAt))
        assertEquals("TESTER", tx.holderName)
        assertTrue(tx.memo!!.startsWith("Monthly student pack"))
        // Tikimt 9, 2019
        assertEquals(
            EthiopianDate(2019, 2, 9),
            EthiopianCalendar.fromMillis(tx.occurredAt!!.toEpochMilli())
        )
    }

    @Test
    fun telebirrDailyPackage() {
        val tx = parsed(
            "Dear Tester\nYou have paid ETB 5.00 for package Daily Internet Package 120 MB purchase made for 911000002 on 27/09/2026 16:56:42. Your transaction number is  DIR970C3Z7. Your current balance is ETB 604.68.To download your payment information please click this link: https://transactioninfo.ethiotelecom.et/receipt/DIR970C3Z7\nThank you for using telebirr\nEthio telecom"
        )
        assertEquals(5_00L, tx.principalCents)
        assertEquals(604_68L, tx.balanceAfterCents)
        assertEquals("2026-09-27T16:56:42", local(tx.occurredAt))
    }

    @Test
    fun telebirrWalletReceiptFromAPerson() {
        val tx = parsed(
            "Dear Tester\nYou have received ETB 64.00 from Test Person(2519****1234)  on 29/09/2026 11:57:24. Your transaction number is DIT58SPAKJ. Your current E-Money Account balance is ETB 69.50.\nThank you for using telebirr\nEthio telecom"
        )
        assertEquals(MoneyFlow.IN, tx.flow)
        assertEquals(64_00L, tx.principalCents)
        assertEquals("Test Person", tx.counterparty)
        assertFalse(tx.counterpartyIsInstitution)
        assertEquals(69_50L, tx.balanceAfterCents)
        assertEquals("DIT58SPAKJ", tx.txnId)
        assertEquals("2026-09-29T11:57:24", local(tx.occurredAt))
    }

    @Test
    fun telebirrBankToWalletReceiptUsesIsoDateAndLearnsTheOwnerName() {
        val tx = parsed(
            "Dear Tester,\nYou have received  ETB 65.00 by transaction number DIT68SLW3Y on 2026-09-29 11:54:41 from Commercial Bank of Ethiopia to your telebirr Account 251911000003 - Test Owner Person. Your current balance is ETB 65.15.\nThank you for using telebirr\nEthio telecom"
        )
        assertEquals(MoneyFlow.IN, tx.flow)
        assertEquals(65_00L, tx.principalCents)
        assertEquals("Commercial Bank of Ethiopia", tx.counterparty)
        assertTrue(tx.counterpartyIsInstitution)
        assertEquals(65_15L, tx.balanceAfterCents)
        assertEquals("DIT68SLW3Y", tx.txnId)
        assertEquals("2026-09-29T11:54:41", local(tx.occurredAt))
        assertEquals(listOf("Test Owner Person"), tx.ownerNames)
    }

    // ---------------------------------------------------------------- Bank of Abyssinia

    @Test
    fun boaCreditFromSettlement() {
        val tx = parsed(
            "Dear TESTER, your account 9*****27 was credited with ETB 3,000.00 by A/R - P2P incoming settlement accou. Available Balance:  ETB 47,358.93. Receipt: https://cs.bankofabyssinia.com/slip/?trx=FT25205W917C10104 \nFeedback: https://cs.bankofabyssinia.com/cs/?trx=CFT25205W917C For help, call 8397 (24/7 Toll-Free). Bank of Abyssinia."
        )
        assertEquals(Provider.BOA, tx.provider)
        assertEquals("BOA:27", tx.accountKey)
        assertEquals("BoA ••27", tx.accountLabel)
        assertEquals(MoneyFlow.IN, tx.flow)
        assertEquals(3_000_00L, tx.principalCents)
        assertEquals(47_358_93L, tx.balanceAfterCents)
        assertEquals("FT25205W917C10104", tx.txnId)
        assertTrue(tx.counterpartyIsInstitution)
        assertNull(tx.occurredAt)
    }

    @Test
    fun boaDebitWithoutCounterparty() {
        val tx = parsed(
            "Dear TESTER, your account 9*****27 was debited with ETB 804.60. Available Balance:  ETB 60,102.31. Receipt: https://cs.bankofabyssinia.com/slip/?trx=FT260249WTV083827\nFeedback: https://cs.bankofabyssinia.com/cs/?trx=DFT260249WTV0 For help, call 8397 (24/7 Toll-Free). Bank of Abyssinia."
        )
        assertEquals(MoneyFlow.OUT, tx.flow)
        assertEquals(804_60L, tx.principalCents)
        assertEquals(60_102_31L, tx.balanceAfterCents)
        assertNull(tx.counterparty)
        assertEquals("FT260249WTV083827", tx.txnId)
    }

    @Test
    fun boaAtmCashDepositKeepsTheDotsInTheBranchName() {
        val tx = parsed(
            "Dear TESTER, your account 9*****27 was credited with ETB 15,000.00 by ATM RECYCLER CASH ACCOUNT-TESTVILLE BR. NO 1. Available Balance:  ETB 60,906.91. Receipt: https://cs.bankofabyssinia.com/slip/?trx=FT26024L1NDS10074\nFeedback: https://cs.bankofabyssinia.com/cs/?trx=CFT26024L1NDS For help, call 8397 (24/7 Toll-Free). Bank of Abyssinia."
        )
        assertEquals(15_000_00L, tx.principalCents)
        assertEquals(60_906_91L, tx.balanceAfterCents)
        assertEquals("ATM cash deposit", tx.memo)
        assertEquals("ATM RECYCLER CASH ACCOUNT-TESTVILLE BR. NO 1", tx.counterparty)
    }

    // ---------------------------------------------------------------- CBE bank

    @Test
    fun cbeIncomingTransfer() {
        val tx = parsed(
            "Dear Test Owner Person You have received ETB 25,000.00 from account 1********0933 (Test Sender Name) to your account 1********0761. Your current balance is ETB56,220.05. Thanks for Banking with CBE. https://mbreciept.cbe.com.et/v2-hfHCxHOBZkrTDHAfWnj0  for feedback: https://forms.gle/kGNGQpG3mQCCk3iD6"
        )
        assertEquals(Provider.CBE, tx.provider)
        assertEquals("CBE:0761", tx.accountKey)
        assertEquals("CBE ••0761", tx.accountLabel)
        assertEquals(MoneyFlow.IN, tx.flow)
        assertEquals(25_000_00L, tx.principalCents)
        assertEquals(56_220_05L, tx.balanceAfterCents)
        assertEquals("Test Sender Name", tx.counterparty)
        assertEquals("hfHCxHOBZkrTDHAfWnj0", tx.txnId)
        assertEquals(listOf("Test Owner Person"), tx.ownerNames)
        assertNull(tx.occurredAt)
    }

    @Test
    fun cbeDebitWithNoFeeBreakdown() {
        val tx = parsed(
            "Dear Mr Tester your Account 1********0761 has been debited with ETB 1005.75 including Service charge ETB0.00ETB0.00 and VAT(15%) . Your Current Balance is ETB 53731.86. Thank you for Banking with CBE!. For feedback https://shorturl.at/auUX0"
        )
        assertEquals(MoneyFlow.OUT, tx.flow)
        assertEquals(1005_75L, tx.principalCents)
        assertEquals(0L, tx.feeCents)
        assertEquals(1005_75L, tx.totalCents)
        assertEquals(53_731_86L, tx.balanceAfterCents)
        assertNull(tx.txnId)
        assertEquals("Tester", tx.holderName)
        assertTrue(tx.ownerNames.isEmpty())
    }

    @Test
    fun cbeTransferToAPersonIsOneExpenseWithFeeInside() {
        val tx = parsed(
            "Dear  Test Owner Person You have successfully transferred ETB550.00 from account 1********0761 to account 1********6437 (Test Receiver Name). Service charge of ETB 0.50 and VAT(15%) of ETB0.08 and Disaster Recovery(5%) of 0.03 with total of ETB550.61 .Your current balance is ETB53,010.78. Thanks for Banking with CBE. https://mbreciept.cbe.com.et/v2-hfHCxNJNtb4lSfmaW0g9  for feedback: https://forms.gle/kGNGQpG3mQCCk3iD6"
        )
        assertEquals(MoneyFlow.OUT, tx.flow)
        assertEquals(550_00L, tx.principalCents)
        assertEquals(61L, tx.feeCents)
        assertEquals(550_61L, tx.totalCents)
        assertEquals(53_010_78L, tx.balanceAfterCents)
        assertEquals("Test Receiver Name", tx.counterparty)
        assertEquals("CBE:0761", tx.accountKey)
    }

    @Test
    fun cbeDebitWithServiceChargeAndVatInsideTheTotal() {
        val tx = parsed(
            "Dear Mr Tester your Account 1********0761 has been debited with ETB 1004.03 including Service charge ETB3.50ETB0.00 and VAT(15%) ETB0.53. Your Current Balance is ETB 49535.71. Thank you for Banking with CBE!. For feedback https://shorturl.at/auUX0"
        )
        assertEquals(1004_03L, tx.totalCents)
        assertEquals(4_03L, tx.feeCents)
        assertEquals(1000_00L, tx.principalCents)
        assertEquals(49_535_71L, tx.balanceAfterCents)
    }

    @Test
    fun cbeDebitTransactionNotice() {
        val tx = parsed(
            "Dear Test Owner Person A debit transaction of ETB 330.00 has occurred on your account 1********0761. Service charge of ETB 6.25 and VAT(15%) of ETB 0.95 and Disaster Recovery(5%) of 0.32 with total of ETB 337.52. Your current balance is ETB 48,687.93. Thanks for Banking with CBE. https://mbreciept.cbe.com.et/v2-hfHCxNV9VIVNE2swXP5K  for feedback: https://forms.gle/kGNGQpG3mQCCk3iD6"
        )
        assertEquals(330_00L, tx.principalCents)
        assertEquals(7_52L, tx.feeCents)
        assertEquals(337_52L, tx.totalCents)
        assertEquals(48_687_93L, tx.balanceAfterCents)
    }

    @Test
    fun cbeAmountWrittenWithTrailingPeriodAndOneDecimal() {
        val tx = parsed(
            "Dear Test Other Holder A debit transaction of ETB 30.0. has occurred on your account 1****4712. Service charge of ETB 5.11 and VAT(15%) of ETB0.77 and Disaster Recovery(5%) of 0.26 with total of ETB36.14 .Your current balance is ETB3,263.85. Thanks for Banking with CBE. https://mbreciept.cbe.com.et/v2-hfHCxGiQ7grFXhtENAye  for feedback: https://forms.gle/kGNGQpG3mQCCk3iD6"
        )
        assertEquals("CBE:4712", tx.accountKey)
        assertEquals(30_00L, tx.principalCents)
        assertEquals(6_14L, tx.feeCents)
        assertEquals(36_14L, tx.totalCents)
        assertEquals(3_263_85L, tx.balanceAfterCents)
    }

    @Test
    fun cbeFeeIsTheDifferenceToThePrintedTotal() {
        val tx = parsed(
            "Dear Test Other Holder A debit transaction of ETB 2000.0. has occurred on your account 1****4712. Service charge of ETB 12.60 and VAT(15%) of ETB1.95 and Disaster Recovery(5%) of 0.65 with total of ETB2015.20 .Your current balance is ETB5,089.22. Thanks for Banking with CBE. https://mbreciept.cbe.com.et/v2-hfHCxzYJQhBBGYty9AUD  for feedback: https://forms.gle/kGNGQpG3mQCCk3iD6"
        )
        assertEquals(2000_00L, tx.principalCents)
        assertEquals(15_20L, tx.feeCents)
        assertEquals(2015_20L, tx.totalCents)
    }

    @Test
    fun cbeZeroFeeDebit() {
        val tx = parsed(
            "Dear Test Other Holder A debit transaction of ETB 50.0. has occurred on your account 1****4712. Service charge of ETB 0.00 and VAT(15%) of 0.0 and Disaster Recovery(5%) of 0.00 with total of ETB50.00 .Your current balance is ETB3,500.60. Thanks for Banking with CBE. https://mbreciept.cbe.com.et/v2-hfHCxGiyRTflKOZSsuEs  for feedback: https://forms.gle/kGNGQpG3mQCCk3iD6"
        )
        assertEquals(50_00L, tx.principalCents)
        assertEquals(0L, tx.feeCents)
        assertEquals(3_500_60L, tx.balanceAfterCents)
    }

    @Test
    fun cbeStrayReceiptLinkIsNotATransaction() {
        val r = SmsParser.parse(null, "https://mbreciept.cbe.com.et/v2-hfHCxGjMfmsuTgHp28bY  for feedback: https://forms.gle/kGNGQpG3mQCCk3iD6")
        assertEquals(ParseResult.Unrecognized, r)
    }

    @Test
    fun cbeRealSequenceChainsOrFlagsAGap() {
        val a = parsed("Dear Mr Tester your Account 1********0761 has been debited with ETB 1004.03 including Service charge ETB3.50ETB0.00 and VAT(15%) ETB0.53. Your Current Balance is ETB 49535.71. Thank you for Banking with CBE!. For feedback https://shorturl.at/auUX0")
        val b = parsed("Dear Test Owner Person A debit transaction of ETB 330.00 has occurred on your account 1********0761. Service charge of ETB 6.25 and VAT(15%) of ETB 0.95 and Disaster Recovery(5%) of 0.32 with total of ETB 337.52. Your current balance is ETB 48,687.93. Thanks for Banking with CBE. https://mbreciept.cbe.com.et/v2-hfHCxNV9VIVNE2swXP5K  for feedback: https://forms.gle/kGNGQpG3mQCCk3iD6")
        val c = parsed("Dear  Test Owner Person You have successfully transferred ETB 5,000.00 from account 1********0761 to account 1********7928 (Test Receiver Two). Service charge of ETB 1.00 and VAT(15%) of ETB 0.15 and Disaster Recovery(5%) of 0.05 with total of ETB 5,001.20. Your current balance is ETB 43,686.73. Thanks for Banking with CBE. https://mbreciept.cbe.com.et/v2-hfHCxNVmkcp5Fxjpv8oH  for feedback: https://forms.gle/kGNGQpG3mQCCk3iD6")
        // a -> b: 49,535.71 - 337.52 is not 48,687.93, so a message is missing in between
        assertTrue(BalanceChain.hasGap(a.balanceAfterCents, b.flow, b.totalCents, b.balanceAfterCents))
        // b -> c chains exactly
        assertFalse(BalanceChain.hasGap(b.balanceAfterCents, c.flow, c.totalCents, c.balanceAfterCents))
    }

    // ---------------------------------------------------------------- CBE Birr

    @Test
    fun cbeBirrAtmVoucherMovesNoMoney() {
        val r = SmsParser.parse(
            null,
            "Dear Customer, as per your request for CBE Birr ATM cash out voucher, the voucher number is 99502, Amount 200.00Br.. The voucher will expire after 24 Hr at 02/03/26 20:37. Cash out the money from your nearby CBE ATM. Thank you!"
        )
        assertTrue(r is ParseResult.Ignored)
    }

    @Test
    fun cbeBirrAtmWithdrawalCountsFeesInsideOneExpense() {
        val tx = parsed(
            "Dear Tester, you have withdrawn 200.00Br. from CBE ATM on 01/03/26 20:39,Txn ID DC131754IML. You have paid 0.15Br. for 5% Disaster Risk Response Fund & 15% Tax and Services Charge 0.70Br.. Your CBE Birr account balance is 57.03Br.Thank You! For your feedback please click the link https://shorturl.at/gy3A0"
        )
        assertEquals(Provider.CBE_BIRR, tx.provider)
        assertEquals("CBEBIRR", tx.accountKey)
        assertEquals(MoneyFlow.OUT, tx.flow)
        assertEquals(200_00L, tx.principalCents)
        assertEquals(85L, tx.feeCents)
        assertEquals(200_85L, tx.totalCents)
        assertEquals(57_03L, tx.balanceAfterCents)
        assertEquals("DC131754IML", tx.txnId)
        assertEquals("2026-03-01T20:39", local(tx.occurredAt))
        // Yekatit 22, 2018
        assertEquals(
            EthiopianDate(2018, 6, 22),
            EthiopianCalendar.fromMillis(tx.occurredAt!!.toEpochMilli())
        )
    }

    @Test
    fun cbeBirrCredit() {
        val tx = parsed(
            "Dear Tester, your CBE Birr account has been credited with 200.00Br. on 04/04/26 07:18,Txn ID DD4219S1E1I. Your balance is 457.03Br. Thank you!"
        )
        assertEquals(MoneyFlow.IN, tx.flow)
        assertEquals(200_00L, tx.principalCents)
        assertEquals(457_03L, tx.balanceAfterCents)
        assertEquals("2026-04-04T07:18", local(tx.occurredAt))
        assertEquals("DD4219S1E1I", tx.txnId)
    }

    @Test
    fun cbeBirrAirtimePurchase() {
        val tx = parsed(
            "Dear Tester, you bought 37.00Br. of Airtime for 911000004 on 09/04/26 07:44,Txn ID DD911A6AY5Z your CBE Birr account balance is 420.03Br. Thank you for choosing CBE Birr! For your feedback please click the link https://shorturl.at/gy3A0 For invoice https://cbepay1.cbe.com.et/aureceipt?TID=DD911A6AY5Z&PH=251911000005"
        )
        assertEquals(MoneyFlow.OUT, tx.flow)
        assertEquals(37_00L, tx.principalCents)
        assertEquals(420_03L, tx.balanceAfterCents)
        assertEquals("2026-04-09T07:44", local(tx.occurredAt))
        assertTrue(tx.memo!!.contains("Airtime"))
    }

    @Test
    fun cbeBirrAirtimeReceivedNoticeIsIgnored() {
        val r = SmsParser.parse(
            null,
            "Dear Customer,you received  37.00Br. airtime from  251911000005 - Test Owner Person on 09/04/26 07:44. Thank you for choosing CBE Birr! For Invoice use link https://cbepay1.cbe.com.et/aureceipt?TID=DD911A6AY5Z&PH=251911000005"
        )
        assertTrue(r is ParseResult.Ignored)
    }

    // ---------------------------------------------------------------- M-PESA

    @Test
    fun mpesaAmharicIncomeFromBank() {
        val tx = parsed(
            "ውድ Tester፣ 50.00 ብር ከ 9000815 - Bank of Abyssinia B2C በ 12/9/25 በ 6:20 PM ላይ ተቀብለዋል። የገንዘብ ዝውውር መለያ ቁጥር TIC33AP8ST ነው። የእርስዎ አሁን ያለው የM-PESA ቀሪ ሒሳብ 51.00 ብር ነው።"
        )
        assertEquals(Provider.MPESA, tx.provider)
        assertEquals(MoneyFlow.IN, tx.flow)
        assertEquals(50_00L, tx.principalCents)
        assertEquals(51_00L, tx.balanceAfterCents)
        assertEquals("TIC33AP8ST", tx.txnId)
        assertEquals("2025-09-12T18:20", local(tx.occurredAt))
        assertTrue(tx.counterpartyIsInstitution)
        assertEquals("Tester", tx.holderName)
        // Meskerem 2, 2018
        assertEquals(
            EthiopianDate(2018, 1, 2),
            EthiopianCalendar.fromMillis(tx.occurredAt!!.toEpochMilli())
        )
    }

    @Test
    fun mpesaAmharicSafaricomPackagePurchase() {
        val tx = parsed(
            "ውድ Tester፣ የ5.00 ብር የሳፋሪኮም ጥቅል ለ251700000001 በ17/9/25 በ5:58 AM ላይ ገዝተዋል። የገንዘብ ዝውውር መለያ ቁጥር TIH03LAQPA ነው። የአገልግሎት ክፍያ 0.00 ብር ነው። አሁን ያለዎት የM-PESA ቀሪ ሒሳብ 31.00 ብር ነው።"
        )
        assertEquals(MoneyFlow.OUT, tx.flow)
        assertEquals(5_00L, tx.principalCents)
        assertEquals(0L, tx.feeCents)
        assertEquals(31_00L, tx.balanceAfterCents)
        assertEquals("TIH03LAQPA", tx.txnId)
        assertEquals("2025-09-17T05:58", local(tx.occurredAt))
    }

    // ---------------------------------------------------------------- eBirr

    @Test
    fun ebirrTransferToAPerson() {
        val tx = parsed(
            "EBIRR-KAAFI-> Id: 802769968889, You sent ETB410, Charges:ETB1 with VAT:ETB to Test Person Name(0911000006) at 11/11/26 20:52:02, Your Balance is ETB413.66.\nhttps://receipt.ebirr.com/kaafimf/5SQ4RKVsh2f5XUQXtN24bw"
        )
        assertEquals(Provider.EBIRR, tx.provider)
        assertEquals(MoneyFlow.OUT, tx.flow)
        assertEquals(410_00L, tx.principalCents)
        assertEquals(1_00L, tx.feeCents)
        assertEquals(411_00L, tx.totalCents)
        assertEquals(413_66L, tx.balanceAfterCents)
        assertEquals("802769968889", tx.txnId)
        assertEquals("Test Person Name", tx.counterparty)
        assertEquals("2026-11-11T20:52:02", local(tx.occurredAt))
    }

    // ---------------------------------------------------------------- not transactions

    @Test
    fun otpAndFailedMessagesAreIgnored() {
        val otp = SmsParser.parse("127", "Your telebirr verification code is 482913. Do not share it with anyone.")
        assertTrue(otp is ParseResult.Ignored)
        val failed = SmsParser.parse(null, "Dear Tester, your telebirr payment of ETB 50.00 failed. Please try again.")
        assertTrue(failed is ParseResult.Ignored)
    }

    @Test
    fun unrelatedMessagesAreUnrecognised() {
        assertEquals(ParseResult.Unrecognized, SmsParser.parse("Mom", "Come home early, dinner is at 7"))
        assertEquals(ParseResult.Unrecognized, SmsParser.parse(null, ""))
        assertEquals(ParseResult.Unrecognized, SmsParser.parse("127", "Dear customer, enjoy 50% off bundles with telebirr this week!"))
    }

    @Test
    fun moneyParsingAndFormatting() {
        assertEquals(300_000L, Money.parse("3,000.00"))
        assertEquals(30_00L, Money.parse("30.0"))
        assertEquals(36_14L, Money.parse("36.14"))
        assertEquals("1,234.56", Money.format(123_456))
        assertEquals("−419.15", Money.formatSigned(-419_15))
        assertEquals("+3,500.00", Money.formatSigned(3_500_00))
    }
}
