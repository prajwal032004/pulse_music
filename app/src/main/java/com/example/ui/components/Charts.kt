package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PulseTheme

/** "7h 24m" style formatting for listening minutes. */
fun formatMinutes(minutes: Float): String {
    val m = minutes.toInt()
    return when {
        m >= 60 -> "${m / 60}h ${m % 60}m"
        else -> "${m}m"
    }
}

/**
 * Single-series bar chart for listening minutes. Bars are anchored to the baseline with rounded
 * data-ends; the highlighted (latest) bar carries the full accent gradient. Tap a bar to reveal
 * its value — the touch equivalent of a hover tooltip.
 */
@Composable
fun ListeningBarChart(
    values: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    highlightIndex: Int = values.lastIndex,
    description: String = "Listening minutes chart"
) {
    val colors = PulseTheme.colors
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(values) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val max = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    val shownIndex = selected ?: highlightIndex

    Column(
        modifier.semantics {
            contentDescription = description + ": " + labels.zip(values).joinToString { (l, v) -> "$l ${formatMinutes(v)}" }
        }
    ) {
        // Value callout for the selected / highlighted bar
        Box(Modifier.fillMaxWidth().height(20.dp)) {
            if (shownIndex in values.indices) {
                Text(
                    "${labels.getOrElse(shownIndex) { "" }} · ${formatMinutes(values[shownIndex])}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(values) {
                    detectTapGestures { offset ->
                        val slot = size.width / values.size.coerceAtLeast(1)
                        val idx = (offset.x / slot).toInt().coerceIn(0, values.lastIndex)
                        selected = if (selected == idx) null else idx
                    }
                }
        ) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.42f).coerceAtMost(22.dp.toPx())
            val radius = 4.dp.toPx().coerceAtMost(barW / 2)
            // Recessive baseline
            drawLine(colors.textTertiary.copy(alpha = 0.35f), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            values.forEachIndexed { i, v ->
                val h = (v / max * (size.height - 4.dp.toPx())).coerceAtLeast(if (v > 0f) 3.dp.toPx() else 2.dp.toPx()) * grow.value
                val x = i * slot + (slot - barW) / 2
                val isShown = i == shownIndex
                val brush = if (isShown) {
                    Brush.verticalGradient(listOf(colors.accentPartner, colors.accent), startY = size.height - h, endY = size.height)
                } else {
                    Brush.verticalGradient(listOf(colors.accent.copy(alpha = 0.55f), colors.accent.copy(alpha = 0.30f)), startY = size.height - h, endY = size.height)
                }
                drawTopRoundedBar(brush, x, size.height - h, barW, h, radius)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (i == shownIndex) colors.textPrimary else colors.textTertiary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Bar with 4dp rounded top corners and a square base sitting on the baseline. */
private fun DrawScope.drawTopRoundedBar(brush: Brush, x: Float, y: Float, w: Float, h: Float, r: Float) {
    if (h <= r * 2) {
        drawRoundRect(brush, Offset(x, y), Size(w, h), CornerRadius(minOf(r, h / 2)))
        return
    }
    val path = Path().apply {
        moveTo(x, y + h)
        lineTo(x, y + r)
        quadraticBezierTo(x, y, x + r, y)
        lineTo(x + w - r, y)
        quadraticBezierTo(x + w, y, x + w, y + r)
        lineTo(x + w, y + h)
        close()
    }
    drawPath(path, brush)
}
