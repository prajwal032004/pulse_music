package com.example.ui.screens

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lyrics.LyricsEngine
import com.example.model.LyricsData
import com.example.model.PlaybackState
import com.example.ui.components.*
import com.example.ui.theme.PulseTheme
import kotlin.math.abs

@Composable
fun LyricsScreen(
    playbackState: PlaybackState,
    fontScale: Float,
    onFontScaleChange: (Float) -> Unit,
    onBack: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong
    val colors = PulseTheme.colors
    var offsetMs by remember(song?.id) { mutableLongStateOf(0L) }
    var lyrics by remember { mutableStateOf<LyricsData?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(song?.id) {
        lyrics = null
        if (song != null) lyrics = LyricsEngine.getLyricsForSong(song, 0L)
    }

    val position = playbackState.currentPositionMs + offsetMs
    val lines = lyrics?.lines.orEmpty()
    val active = remember(lines, position) { lines.indexOfLast { it.timestampMs <= position }.coerceAtLeast(0) }

    LaunchedEffect(active, lines) {
        if (lyrics?.isSynced == true && lines.isNotEmpty()) {
            // +1 accounts for the leading spacer item; keep the active line in the upper third.
            listState.animateScrollToItem((active + 1 - 2).coerceAtLeast(0))
        }
    }

    val palette = remember(song?.artworkKey()) { artworkPalette(song?.artworkKey() ?: "lyrics") }

    AuroraBackground(modifier.fillMaxSize(), palette[0], palette[1], palette[2], strength = 1.4f) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0.4f), colors.background.copy(alpha = 0.75f)))))
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.KeyboardArrowDown, "Close lyrics", onBack, modifier = Modifier.testTag("lyrics_back_button"))
                Spacer(Modifier.width(12.dp))
                if (song != null) {
                    SongArtwork(song, Modifier.size(44.dp), RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(song.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val data = lyrics
                when {
                    data == null -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.align(Alignment.Center))
                    data.isSynced && lines.isNotEmpty() -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        item { Spacer(Modifier.height(80.dp)) }
                        itemsIndexed(lines) { index, line ->
                            val distance = abs(index - active)
                            val isActive = index == active
                            val size by animateFloatAsState((if (isActive) 30f else 22f) * fontScale, tween(300), label = "lyricSize")
                            val alpha by animateFloatAsState(
                                when {
                                    isActive -> 1f
                                    index < active -> 0.35f
                                    else -> (0.6f - distance * 0.08f).coerceAtLeast(0.2f)
                                },
                                tween(300), label = "lyricAlpha"
                            )
                            val blurRadius = if (isActive || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) 0.dp else (distance.coerceAtMost(4) * 0.8f).dp
                            val baseColor by animateColorAsState(if (isActive) colors.textPrimary else colors.textPrimary.copy(alpha = alpha), label = "lyricColor")
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = size.sp,
                                    lineHeight = (size * 1.25f).sp,
                                    brush = if (isActive) Brush.linearGradient(colors.accentGradientColors) else null
                                ),
                                color = baseColor,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSeekTo((line.timestampMs - offsetMs).coerceAtLeast(0)) }
                                    .blur(blurRadius)
                                    .padding(vertical = 4.dp)
                            )
                        }
                        item { Spacer(Modifier.height(240.dp)) }
                    }
                    else -> EmptyState(
                        Icons.Rounded.Lyrics,
                        "No synced lyrics",
                        data.plainText.ifBlank { "Add a matching .lrc file next to this song to enable karaoke lyrics." },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Offset & font controls
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LyricControl(Icons.Rounded.Remove, "−0.1s", { offsetMs -= 100 })
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .glass(colors, RoundedCornerShape(22.dp))
                        .clickable { offsetMs = 0 },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (offsetMs == 0L) "Sync offset 0.0s" else "Offset ${if (offsetMs > 0) "+" else ""}${"%.1f".format(offsetMs / 1000f)}s",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textPrimary
                    )
                }
                LyricControl(Icons.Rounded.Add, "+0.1s", { offsetMs += 100 })
                LyricControl(Icons.Rounded.TextFields, "Aa", {
                    val next = when {
                        fontScale < 0.95f -> 1f
                        fontScale < 1.15f -> 1.25f
                        fontScale < 1.4f -> 1.5f
                        else -> 0.85f
                    }
                    onFontScaleChange(next)
                })
            }

            // Slim player bar
            if (song != null) {
                val progress = if (playbackState.durationMs > 0) (playbackState.currentPositionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f) else 0f
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .glass(colors, RoundedCornerShape(22.dp), strong = true)
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatDuration(playbackState.currentPositionMs), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        Spacer(Modifier.weight(1f))
                        Text(formatDuration(playbackState.durationMs), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        Spacer(Modifier.width(8.dp))
                        GradientPlayButton(playbackState.isPlaying, onPlayPause, size = 44.dp, glow = false)
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(colors.textPrimary.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).clip(RoundedCornerShape(2.dp)).background(colors.accentGradient))
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    GlassIconButton(icon, label, onClick, size = 44.dp, iconSize = 20.dp)
}
