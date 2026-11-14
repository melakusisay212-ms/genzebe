package com.genzebe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.genzebe.app.core.EthiopianCalendar
import com.genzebe.app.core.EthiopianDate
import com.genzebe.app.core.Money
import com.genzebe.app.core.TxKind
import com.genzebe.app.data.TxEntity

@Composable
fun TxDetailDialog(
    tx: TxEntity,
    accountLabel: String,
    ethiopianClock: Boolean,
    onDismiss: () -> Unit,
    onSetKind: (TxKind) -> Unit,
    onConfirm: () -> Unit,
    onPickDate: () -> Unit,
    onDelete: () -> Unit
) {
    var askDelete by remember { mutableStateOf(false) }
    val kind = tx.kindEnum()
    val date = EthiopianCalendar.fromMillis(tx.occurredAt)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            TextButton(onClick = { askDelete = true }) { Text("Delete", color = G.Expense) }
        },
        title = {
            Column {
                Text(tx.amountText(), fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = when (kind) {
                        TxKind.INCOME -> G.Income
                        TxKind.EXPENSE -> G.Expense
                        TxKind.TRANSFER -> G.Neutral
                    })
                Text(tx.title(), fontSize = 14.sp, color = G.Muted)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailLine("Account", accountLabel)
                DetailLine("Date", EthiopianCalendar.format(date) + " · " + Fmt.time(tx.occurredAt, ethiopianClock))
                if (tx.feeCents > 0) {
                    DetailLine("Amount", Money.format(tx.principalCents))
                    DetailLine("Fee", Money.format(tx.feeCents))
                    DetailLine("Total", Money.format(tx.totalCents))
                }
                tx.balanceAfterCents?.let { DetailLine("Balance after", Money.format(it)) }
                tx.txnId?.let { DetailLine("Reference", it) }

                if (tx.needsAttention()) {
                    Text(
                        "The app is not fully sure about this one. Check it against the message below.",
                        fontSize = 13.sp,
                        color = G.ReviewText
                    )
                    GButton("It is correct", onConfirm, Modifier.fillMaxWidth())
                }

                Text("Type", fontSize = 12.sp, color = G.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GChip("Expense", kind == TxKind.EXPENSE, { onSetKind(TxKind.EXPENSE) }, compact = true)
                    GChip("Income", kind == TxKind.INCOME, { onSetKind(TxKind.INCOME) }, compact = true)
                    GChip("Transfer", kind == TxKind.TRANSFER, { onSetKind(TxKind.TRANSFER) }, compact = true)
                }
                GButton("Change date", onPickDate, Modifier.fillMaxWidth(), filled = false)

                Text("Original message", fontSize = 12.sp, color = G.Muted)
                Text(tx.rawBody, fontSize = 12.sp, color = G.Ink)
            }
        }
    )

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Delete this transaction?") },
            text = { Text("It is removed from your totals. If the same message arrives again it will be added again.") },
            confirmButton = {
                TextButton(onClick = { askDelete = false; onDelete() }) { Text("Delete", color = G.Expense) }
            },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Keep") } }
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = G.Muted)
        Spacer(Modifier.width(12.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = G.Ink)
    }
}

/** Picks an Ethiopian date with steppers, so the user never has to think in Gregorian. */
@Composable
fun EthDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onPicked: (year: Int, month: Int, day: Int) -> Unit
) {
    val start = remember { EthiopianCalendar.fromMillis(initialMillis) }
    var year by remember { mutableStateOf(start.year) }
    var month by remember { mutableStateOf(start.month) }
    var day by remember { mutableStateOf(start.day) }
    val maxDay = EthiopianCalendar.daysInMonth(year, month)
    if (day > maxDay) day = maxDay

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change date") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper(
                    label = "Year",
                    value = year.toString(),
                    onMinus = { year -= 1 },
                    onPlus = { year += 1 }
                )
                Stepper(
                    label = "Month",
                    value = EthiopianCalendar.monthName(month),
                    onMinus = { month = if (month == 1) 13 else month - 1 },
                    onPlus = { month = if (month == 13) 1 else month + 1 }
                )
                Stepper(
                    label = "Day",
                    value = day.toString(),
                    onMinus = { day = if (day <= 1) EthiopianCalendar.daysInMonth(year, month) else day - 1 },
                    onPlus = { day = if (day >= EthiopianCalendar.daysInMonth(year, month)) 1 else day + 1 }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onPicked(year, month, day) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = G.Muted, modifier = Modifier.width(56.dp))
        TextButton(onClick = onMinus) { Text("−", fontSize = 20.sp) }
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = G.Ink, modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        TextButton(onClick = onPlus) { Text("+", fontSize = 20.sp) }
    }
}

@Composable
fun EditBalanceDialog(
    label: String,
    currentCents: Long?,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var text by remember {
        mutableStateOf(if (currentCents == null) "" else Money.format(currentCents).replace(",", ""))
    }
    val parsed: Long? = try {
        if (text.isBlank()) null else Money.parse(text)
    } catch (e: Exception) {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$label balance") },
        text = {
            Column {
                Text(
                    "Set what the account really holds now. It stays until a newer message shows a balance.",
                    fontSize = 13.sp,
                    color = G.Muted
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Balance in ETB") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun PasteMessageDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit, initial: String = "") {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a message") },
        text = {
            Column {
                Text("Paste a bank or wallet SMS. You can also share it to ገንዘቤ from your messages app.", fontSize = 13.sp, color = G.Muted)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(text) }, enabled = text.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
