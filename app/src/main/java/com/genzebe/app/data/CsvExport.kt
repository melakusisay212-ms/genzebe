package com.genzebe.app.data

import com.genzebe.app.core.EthiopianCalendar
import com.genzebe.app.core.EthiopianClock
import com.genzebe.app.core.Money

/** Builds the CSV the user can save from Settings. One row per transaction, fee included in the amount. */
object CsvExport {
    fun build(txs: List<TxEntity>, accountLabels: Map<String, String>): String {
        val sb = StringBuilder()
        sb.append("ethiopian_date,gregorian_date,time,account,type,amount,fee_included,counterparty,note,balance_after,transaction_id\n")
        for (tx in txs.sortedBy { it.occurredAt }) {
            val date = EthiopianCalendar.localDateOf(tx.occurredAt)
            val eth = EthiopianCalendar.fromLocalDate(date)
            val row = listOf(
                "${eth.year}-${eth.month.toString().padStart(2, '0')}-${eth.day.toString().padStart(2, '0')}",
                date.toString(),
                EthiopianClock.formatStandard(tx.occurredAt),
                accountLabels[tx.accountKey] ?: tx.accountKey,
                tx.kind.lowercase(),
                Money.format(tx.totalCents).replace(",", ""),
                Money.format(tx.feeCents).replace(",", ""),
                tx.counterparty ?: "",
                tx.memo ?: "",
                tx.balanceAfterCents?.let { Money.format(it).replace(",", "") } ?: "",
                tx.txnId ?: ""
            )
            sb.append(row.joinToString(",") { escape(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun escape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
