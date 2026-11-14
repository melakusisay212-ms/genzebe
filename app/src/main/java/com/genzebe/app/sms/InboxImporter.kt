package com.genzebe.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.genzebe.app.data.RawSms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads existing messages from the phone's inbox. Needs READ_SMS, which only the "auto" build declares. */
object InboxImporter {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** All inbox messages received at or after [sinceMillis], oldest first. */
    suspend fun read(context: Context, sinceMillis: Long): List<RawSms> = withContext(Dispatchers.IO) {
        val result = ArrayList<RawSms>()
        try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(sinceMillis.toString()),
                "${Telephony.Sms.DATE} ASC"
            )
            cursor?.use { c ->
                val address = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (c.moveToNext()) {
                    val text = c.getString(body) ?: continue
                    result.add(RawSms(c.getString(address), text, c.getLong(date)))
                }
            }
        } catch (e: SecurityException) {
            // permission missing: nothing to read
        }
        result
    }
}
