package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.PlaybackState
import com.example.ui.theme.PulseTheme

@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong ?: return
    val colors = PulseTheme.colors
    var dragX by remember { mutableFloatStateOf(0f) }
    val offset by animateFloatAsState(dragX, label = "miniDrag")
    val palette = remember(song.artworkKey()) { artworkPalette(song.artworkKey()) }
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .softGlow(palette.first(), radius = 24.dp, cornerRadius = 24.dp, alpha = 0.35f * PulseTheme.motion)
            .graphicsLayer { translationX = offset * 0.35f }
            .clip(shape)
            .background(if (colors.isDark) Color(0xFF0C0C12) else Color(0xFFFFFFFF))
            .glass(colors, shape, strong = true, tint = if (colors.isDark) palette.first().copy(alpha = 0.5f) else null)
            .clickable(onClick = onClick)
            .draggable(
                state = rememberDraggableState { delta -> dragX += delta },
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (dragX < -120) onNext() else if (dragX > 120) onPrevious()
                    dragX = 0f
                }
            )
            .testTag("mini_player")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SongArtwork(song, Modifier.size(48.dp), RoundedCornerShape(14.dp), iconSize = 20.dp)
            Spacer(Modifier.width(12.dp))
            AnimatedContent(
                targetState = song,
                transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
                modifier = Modifier.weight(1f),
                label = "miniTitle"
            ) { s ->
                Column {
                    Text(
                        s.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        s.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            GradientPlayButton(
                isPlaying = playbackState.isPlaying,
                onClick = onPlayPause,
                size = 44.dp,
                glow = false,
                modifier = Modifier.testTag("mini_play_pause_button")
            )
            PulseIconButton(
                icon = Icons.Rounded.SkipNext,
                contentDescription = "Next song",
                onClick = onNext,
                modifier = Modifier.testTag("mini_next_button"),
                iconSize = 28.dp
            )
        }

        // Thin gradient progress line
        val progress = if (playbackState.durationMs > 0) (playbackState.currentPositionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f) else 0f
        Canvas(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .height(3.dp)
        ) {
            drawRoundRect(colors.textPrimary.copy(alpha = 0.12f), cornerRadius = CornerRadius(size.height))
            drawRoundRect(
                brush = Brush.horizontalGradient(colors.accentGradientColors, endX = size.width),
                size = Size(size.width * progress, size.height),
                topLeft = Offset.Zero,
                cornerRadius = CornerRadius(size.height)
            )
        }
    }
}
