package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PulseTheme
import kotlin.math.abs
import kotlin.math.sin

/**
 * Waveform-style seek bar. Bars are derived from the track id so every song has its own
 * consistent "shape". While dragging, the preview position is shown and seeking happens on release.
 */
@Composable
fun WaveformSeekBar(
    currentPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    seed: Long = 0L,
    barCount: Int = 56,
    height: Dp = 56.dp,
    onScrub: ((Long?) -> Unit)? = null
) {
    val colors = PulseTheme.colors
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrub by rememberUpdatedState(onScrub)
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val playedFraction = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val fraction = dragFraction ?: playedFraction

    val heights = remember(barCount, seed) {
        val s = abs(seed % 997).toFloat()
        List(barCount) { i ->
            val f = sin(i * 0.33f + s) * 0.38f + sin(i * 0.87f + s * 0.5f) * 0.22f + sin(i * 0.13f) * 0.15f + 0.45f
            f.coerceIn(0.14f, 1f)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                contentDescription = "Seek bar"
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { target -> currentOnSeek((target * durationMs).toLong()); true }
            }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    currentOnSeek((f * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        currentOnScrub?.invoke((dragFraction!! * durationMs).toLong())
                    },
                    onDragEnd = {
                        dragFraction?.let { currentOnSeek((it * durationMs).toLong()) }
                        dragFraction = null
                        currentOnScrub?.invoke(null)
                    },
                    onDragCancel = {
                        dragFraction = null
                        currentOnScrub?.invoke(null)
                    }
                ) { change, _ ->
                    change.consume()
                    dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    currentOnScrub?.invoke((dragFraction!! * durationMs).toLong())
                }
            }
    ) {
        val slot = size.width / barCount
        val barWidth = (slot * 0.56f).coerceAtLeast(2.dp.toPx())
        val played = Brush.horizontalGradient(colors.accentGradientColors, startX = 0f, endX = size.width)
        val scale = if (dragFraction != null) 1.08f else 1f

        for (i in 0 until barCount) {
            val x = i * slot + (slot - barWidth) / 2
            val barCenter = (i + 0.5f) / barCount
            val h = size.height * heights[i] * 0.9f * scale
            val y = (size.height - h) / 2
            if (barCenter <= fraction) {
                drawRoundRect(
                    brush = played,
                    topLeft = Offset(x, y),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2)
                )
            } else {
                drawRoundRect(
                    color = colors.textPrimary.copy(alpha = if (colors.isDark) 0.18f else 0.16f),
                    topLeft = Offset(x, y),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2)
                )
            }
        }

        val headX = fraction * size.width
        drawCircle(colors.accent.copy(alpha = 0.25f), radius = 14.dp.toPx(), center = Offset(headX, size.height / 2))
        drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(headX, size.height / 2))
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
