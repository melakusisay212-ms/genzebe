package com.genzebe.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.genzebe.app.ui.AppRoot
import com.genzebe.app.ui.GenzebeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GenzebeTheme {
                AppRoot()
            }
        }
    }
}
