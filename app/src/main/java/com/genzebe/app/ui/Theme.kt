package com.genzebe.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The palette from the approved designs. Expense is blood red, income is green. */
object G {
    val Ground = Color(0xFFF4F6F3)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0E1B16)
    val Muted = Color(0xFF5A6660)
    val Border = Color(0xFFE3E8E4)
    val Divider = Color(0xFFEEF1EF)

    val Hero = Color(0xFF0F2D24)
    val HeroMuted = Color(0xFFB5DCCB)
    val Lime = Color(0xFFC8F169)
    val Accent = Color(0xFF0A7A55)

    val Income = Color(0xFF0A7A55)
    val IncomeBg = Color(0xFFE3F4EC)
    val Expense = Color(0xFFB3001B)
    val ExpenseBg = Color(0xFFFBE3E5)
    val Neutral = Color(0xFF5A6660)
    val NeutralBg = Color(0xFFEDEFEC)

    val ReviewBg = Color(0xFFFFF1D6)
    val ReviewText = Color(0xFF7A4B00)
    val ReviewDot = Color(0xFFC77700)

    /** Calendar heat, from a quiet day to the busiest day of the month. */
    val Heat = listOf(
        Color(0xFFFDECEE),
        Color(0xFFF9D0D5),
        Color(0xFFF2A8B0),
        Color(0xFFE67783)
    )
    val HeatText = Color(0xFF3D0008)

    val LastPeriod = Color(0xFFC9D1CC)
}

@Composable
fun GenzebeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = G.Accent,
            onPrimary = Color.White,
            background = G.Ground,
            onBackground = G.Ink,
            surface = G.Surface,
            onSurface = G.Ink,
            error = G.Expense
        ),
        content = content
    )
}
