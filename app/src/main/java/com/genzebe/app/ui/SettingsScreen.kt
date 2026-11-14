package com.genzebe.app.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genzebe.app.BuildConfig
import com.genzebe.app.sms.InboxImporter

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var askClear by remember { mutableStateOf(false) }
    var showPaste by remember { mutableStateOf(false) }
    var pasteInitial by remember { mutableStateOf("") }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) vm.exportCsv(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹ Back",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = G.Accent,
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 10.dp, horizontal = 4.dp)
            )
        }
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = G.Ink)

        GCard(padding = 18.dp) {
            ToggleRow("Ethiopian clock", "Show 6 o'clock as 12 at sunrise", vm.ethiopianClock) { vm.setEthiopianClock(it) }
            RowDivider()
            ToggleRow("Hide balances", "Show dots instead of amounts", vm.hideBalance) { vm.setHideBalance(it) }
        }

        GCard(padding = 18.dp) {
            Text("Messages", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = G.Muted)
            Spacer(Modifier.height(10.dp))
            if (BuildConfig.AUTO_READ) {
                Text(
                    if (InboxImporter.hasPermission(context)) "ገንዘቤ reads new bank and wallet messages as they arrive."
                    else "SMS permission is off. New messages are not being read.",
                    fontSize = 14.sp,
                    color = G.Ink
                )
                Spacer(Modifier.height(10.dp))
                GButton("Read this month again", { vm.importThisMonth() }, Modifier.fillMaxWidth(), filled = false)
                Spacer(Modifier.height(8.dp))
            } else {
                Text(
                    "This build does not read your SMS. Long-press a bank message, choose Share, then ገንዘቤ. Or paste it here.",
                    fontSize = 14.sp,
                    color = G.Ink
                )
                Spacer(Modifier.height(10.dp))
            }
            GButton(
                "Paste a message",
                {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    pasteInitial = cm.primaryClip?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    showPaste = true
                },
                Modifier.fillMaxWidth(),
                filled = false
            )
        }

        GCard(padding = 18.dp) {
            Text("Your data", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = G.Muted)
            Spacer(Modifier.height(10.dp))
            GButton("Export to CSV", { exporter.launch("genzebe.csv") }, Modifier.fillMaxWidth(), filled = false)
            Spacer(Modifier.height(8.dp))
            GButton("Delete all data", { askClear = true }, Modifier.fillMaxWidth(), filled = false)
            Spacer(Modifier.height(10.dp))
            Text(
                "Everything stays on this phone. The app has no internet access.",
                fontSize = 12.sp,
                color = G.Muted
            )
        }

        Text(
            "ገንዘቤ ${BuildConfig.VERSION_NAME}",
            fontSize = 12.sp,
            color = G.Muted
        )
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text("Delete all data?") },
            text = { Text("Every transaction, account and balance is removed from this phone. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { askClear = false; vm.clearAll() }) { Text("Delete", color = G.Expense) }
            },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text("Keep") } }
        )
    }
    if (showPaste) {
        PasteMessageDialog(
            initial = pasteInitial,
            onDismiss = { showPaste = false },
            onAdd = { showPaste = false; vm.addPastedMessage(it) }
        )
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = G.Ink)
            Text(subtitle, fontSize = 12.sp, color = G.Muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
