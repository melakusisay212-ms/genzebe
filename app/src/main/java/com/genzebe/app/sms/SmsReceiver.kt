package com.genzebe.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.genzebe.app.GenzebeApp
import kotlinx.coroutines.launch

/**
 * Reads each incoming SMS as it arrives. Only declared in the "auto" build (src/auto manifest).
 * Messages that are not bank or wallet transactions are parsed and thrown away; nothing
 * leaves the phone.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts == null || parts.isEmpty()) return

        val sender = parts[0].originatingAddress
        val body = parts.joinToString(separator = "") { it.messageBody ?: "" }
        if (body.isBlank()) return

        val app = context.applicationContext as? GenzebeApp ?: return
        if (!app.prefs.onboardingDone) return

        // The phone's own clock is the time of arrival; many bank SMS carry no date at all.
        val receivedAt = System.currentTimeMillis()
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.repo.ingest(sender, body, receivedAt)
            } finally {
                pending.finish()
            }
        }
    }
}
