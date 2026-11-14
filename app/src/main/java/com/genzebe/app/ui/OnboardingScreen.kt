package com.genzebe.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genzebe.app.BuildConfig
import com.genzebe.app.core.EthiopianCalendar
import com.genzebe.app.data.Prefs

/** First launch: read this Ethiopian month's messages, or start from today. */
@Composable
fun OnboardingScreen(vm: MainViewModel) {
    var mode by rememberSaveable { mutableStateOf(Prefs.START_MONTH) }
    val eth = EthiopianCalendar.today()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> vm.finishOnboarding(mode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("ገንዘቤ", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = G.Ink)
        Text(
            "Your money, counted from the messages your bank and wallets already send. No typing.",
            fontSize = 16.sp,
            color = G.Muted
        )
        Spacer(Modifier.height(8.dp))
        Text("Where should it start?", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = G.Ink)

        ChoiceCard(
            title = "Read ${EthiopianCalendar.monthName(eth.month)} ${eth.year}",
            body = if (BuildConfig.AUTO_READ)
                "Import the bank and wallet messages already on this phone, from day 1 of this Ethiopian month."
            else
                "Start with this Ethiopian month. Share or paste the messages you want counted.",
            selected = mode == Prefs.START_MONTH,
            onClick = { mode = Prefs.START_MONTH }
        )
        ChoiceCard(
            title = "Start from today",
            body = "Ignore everything before now. Only new transactions are counted.",
            selected = mode == Prefs.START_TODAY,
            onClick = { mode = Prefs.START_TODAY }
        )

        if (BuildConfig.AUTO_READ) {
            GCard(padding = 16.dp) {
                Text("Next, Android will ask to read your messages.", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = G.Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Messages are read only on this phone and never leave it. If Android says the setting is restricted, open Settings, Apps, ገንዘቤ, the three dots, and choose Allow restricted settings.",
                    fontSize = 13.sp,
                    color = G.Muted
                )
            }
        } else {
            GCard(padding = 16.dp) {
                Text(
                    "This build does not ask for SMS access. To add a message, long-press it, choose Share, then ገንዘቤ.",
                    fontSize = 13.sp,
                    color = G.Muted
                )
            }
        }

        GButton(
            label = "Continue",
            onClick = {
                if (BuildConfig.AUTO_READ) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
                } else {
                    vm.finishOnboarding(mode)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChoiceCard(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) G.IncomeBg else G.Surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) G.Accent else G.Border, shape)
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = G.Ink)
        Spacer(Modifier.height(4.dp))
        Text(body, fontSize = 14.sp, color = G.Muted)
    }
}
