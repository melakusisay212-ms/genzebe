package com.genzebe.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.genzebe.app.data.IngestOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Receives a message shared from the SMS app ("Share" then Genzebe), saves it, and closes.
 * This is how the build without SMS permission gets its data.
 */
class ShareReceiveActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val app = application as GenzebeApp
        if (text.isNullOrBlank()) {
            finish()
            return
        }
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                app.repo.ingest(null, text, System.currentTimeMillis())
            }
            val message = when (outcome) {
                IngestOutcome.ADDED -> "Saved to ገንዘቤ"
                IngestOutcome.DUPLICATE -> "Already saved"
                IngestOutcome.IGNORED -> "Not a transaction, skipped"
                IngestOutcome.UNRECOGNIZED -> "Could not read this message"
            }
            Toast.makeText(this@ShareReceiveActivity, message, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
