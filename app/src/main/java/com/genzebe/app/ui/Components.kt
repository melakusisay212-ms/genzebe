package com.genzebe.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.genzebe.app.core.Comparison
import com.genzebe.app.core.Money
import com.genzebe.app.core.TxKind
import com.genzebe.app.data.TxEntity

/** White rounded card with a thin border. */
@Composable
fun GCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(G.Surface)
            .border(1.dp, G.Border, shape)
            .padding(padding),
        content = content
    )
}

@Composable
fun Pill(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = foreground,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

@Composable
fun GChip(label: String, selected: Boolean, onClick: () -> Unit, compact: Boolean = false) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .height(if (compact) 36.dp else 44.dp)
            .clip(shape)
            .background(if (selected) G.Hero else G.Surface)
            .border(1.dp, if (selected) G.Hero else G.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else G.Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Dark green button used for the main action on a screen or dialog. */
@Composable
fun GButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(shape)
            .background(if (filled) G.Hero else G.Surface)
            .border(1.dp, if (filled) G.Hero else G.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (filled) Color.White else G.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = G.Ink, modifier = modifier)
}

/** Up or down change against the previous period. For spending, more is red and less is green. */
@Composable
fun DeltaLabel(comparison: Comparison, suffix: String) {
    val pct = comparison.percent
    if (pct == null) {
        Text("no data $suffix", fontSize = 12.sp, color = G.Muted)
        return
    }
    val up = comparison.deltaCents > 0
    val flat = comparison.deltaCents == 0L
    val color = if (flat) G.Muted else if (up) G.Expense else G.Income
    val arrow = if (flat) "" else if (up) "▲ " else "▼ "
    Text(
        text = arrow + Fmt.percent(pct) + " " + suffix,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}

/** One transaction line. Red for expense, green for income, grey for a transfer between own accounts. */
@Composable
fun TxRow(
    tx: TxEntity,
    accountLabel: String,
    ethiopianClock: Boolean,
    onClick: () -> Unit
) {
    val kind = tx.kindEnum()
    val bg = when (kind) {
        TxKind.INCOME -> G.IncomeBg
        TxKind.EXPENSE -> G.ExpenseBg
        TxKind.TRANSFER -> G.NeutralBg
    }
    val fg = when (kind) {
        TxKind.INCOME -> G.Income
        TxKind.EXPENSE -> G.Expense
        TxKind.TRANSFER -> G.Neutral
    }
    val glyph = when (kind) {
        TxKind.INCOME -> "↙"
        TxKind.EXPENSE -> "↗"
        TxKind.TRANSFER -> "⇄"
    }
    val note: String? = when {
        tx.needsAttention() -> "Check this"
        kind == TxKind.TRANSFER -> "not counted"
        kind == TxKind.EXPENSE && tx.feeCents > 0 -> "incl. " + Money.format(tx.feeCents) + " fee"
        else -> null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.title(),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = G.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = accountLabel + " · " + Fmt.time(tx.occurredAt, ethiopianClock),
                fontSize = 12.sp,
                color = G.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(tx.amountText(), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = fg)
            if (note != null) {
                Text(
                    text = note,
                    fontSize = 12.sp,
                    color = if (tx.needsAttention()) G.ReviewText else G.Muted
                )
            }
        }
    }
}

@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(G.Divider))
}

/** Three small bars, used as the Analysis tab icon. */
@Composable
fun BarsIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.14f
        fun bar(x: Float, top: Float) {
            drawLine(
                color = color,
                start = Offset(x, h * 0.88f),
                end = Offset(x, h * top),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        bar(w * 0.2f, 0.5f)
        bar(w * 0.5f, 0.12f)
        bar(w * 0.8f, 0.34f)
    }
}

@Composable
fun VSpace(height: Dp) {
    Spacer(Modifier.height(height))
}

@Composable
fun HSpace(width: Dp) {
    Spacer(Modifier.width(width))
}

/** Arrange children with equal gaps. */
val GapSmall = Arrangement.spacedBy(8.dp)
val GapMedium = Arrangement.spacedBy(12.dp)
val GapLarge = Arrangement.spacedBy(16.dp)
