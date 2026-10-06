package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AudioDeviceType
import com.example.model.OutputDevice
import com.example.model.PlaybackState
import com.example.model.Song
import com.example.smart.ListeningStats
import com.example.ui.components.*
import com.example.ui.theme.PulseTheme
import com.example.ui.viewmodel.HomeFeed
import com.example.ui.viewmodel.SmartPlaylistType
import java.util.Calendar

@Composable
fun HomeScreen(
    feed: HomeFeed,
    stats: ListeningStats,
    playbackState: PlaybackState,
    outputDevice: OutputDevice?,
    onPlaySong: (Song, List<Song>) -> Unit,
    onPlayPause: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenSearch: () -> Unit,
    onPlaySmart: (SmartPlaylistType) -> Unit,
    onOpenSmart: (SmartPlaylistType) -> Unit,
    onSurpriseMe: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenDevices: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    libraryEmpty: Boolean = false,
    hasPermission: Boolean = true,
    onRequestAccess: () -> Unit = {},
    onRescan: () -> Unit = {}
) {
    val colors = PulseTheme.colors
    val current = playbackState.currentSong
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning" to "Start the day with something bright"
            in 12..16 -> "Good afternoon" to "Keep the momentum going"
            in 17..21 -> "Good evening" to "Music always hits different at night"
            else -> "Late night" to "Soft sounds for quiet hours"
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Greeting header + output device pill
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(greeting.first, style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary)
                    Text(greeting.second, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                GlassIconButton(Icons.Rounded.Insights, "Listening stats", onOpenStats)
            }
        }

        item {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Search entry
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .glass(colors, RoundedCornerShape(26.dp))
                        .clickable(onClick = onOpenSearch)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Search, null, tint = colors.textSecondary)
                    Spacer(Modifier.width(12.dp))
                    Text("Search songs, artists, albums…", style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary)
                }
                if (outputDevice != null) OutputDevicePill(outputDevice, onOpenDevices)
            }
        }

        if (libraryEmpty) {
            item {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp), contentPadding = 8.dp) {
                    if (!hasPermission) {
                        EmptyState(
                            Icons.Rounded.LockOpen,
                            "Allow music access",
                            "Pulse needs permission to find the songs on your phone. Nothing leaves your device.",
                            actionLabel = "Allow access",
                            onAction = onRequestAccess
                        )
                    } else {
                        EmptyState(
                            Icons.Rounded.LibraryMusic,
                            "No songs found",
                            "Add MP3, FLAC, M4A or WAV files to your phone (e.g. the Music or Download folder), then rescan.",
                            actionLabel = "Rescan library",
                            onAction = onRescan
                        )
                    }
                }
            }
            return@LazyColumn
        }

        // Continue listening hero
        if (current != null) {
            item {
                ContinueListeningCard(
                    song = current,
                    playbackState = playbackState,
                    onPlayPause = onPlayPause,
                    onOpen = onOpenNowPlaying,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        // Quick moods
        item {
            Column {
                SectionHeader("Quick moods", Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val moods = listOf(
                        Triple("Chill", Icons.Rounded.SelfImprovement, SmartPlaylistType.CHILL),
                        Triple("Workout", Icons.Rounded.FitnessCenter, SmartPlaylistType.WORKOUT),
                        Triple("Focus", Icons.Rounded.Psychology, SmartPlaylistType.FOCUS),
                        Triple("Sleep", Icons.Rounded.Bedtime, SmartPlaylistType.SLEEP)
                    )
                    items(moods) { (label, icon, type) ->
                        PulseChip(label, selected = false, onClick = { onPlaySmart(type) }, icon = icon, iconTint = colors.accent)
                    }
                }
            }
        }

        if (feed.recentlyPlayed.isNotEmpty()) {
            item {
                SongCarousel("Recently played", feed.recentlyPlayed, current?.id, onPlaySong, onSeeAll = { onOpenSmart(SmartPlaylistType.RECENTLY_PLAYED) })
            }
        }

        item {
            GradientButton(
                text = "Surprise Me",
                onClick = onSurpriseMe,
                icon = Icons.Rounded.AutoAwesome,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            )
        }

        item { StatsCard(stats, onOpenStats, Modifier.padding(horizontal = 20.dp)) }

        if (feed.mostPlayed.isNotEmpty()) {
            item { SongCarousel("Most played", feed.mostPlayed, current?.id, onPlaySong, onSeeAll = { onOpenSmart(SmartPlaylistType.MOST_PLAYED) }) }
        }
        if (feed.favorites.isNotEmpty()) {
            item { SongCarousel("Your favorites", feed.favorites, current?.id, onPlaySong, onSeeAll = { onOpenSmart(SmartPlaylistType.LIKED) }) }
        }
        if (feed.throwback.isNotEmpty()) {
            item { ThrowbackCard(feed.throwback, onPlay = { onPlaySong(feed.throwback.first(), feed.throwback) }, Modifier.padding(horizontal = 20.dp)) }
        }
        if (feed.recentlyAdded.isNotEmpty()) {
            item { SongCarousel("Recently added", feed.recentlyAdded, current?.id, onPlaySong, onSeeAll = { onOpenSmart(SmartPlaylistType.RECENTLY_ADDED) }) }
        }
    }
}

@Composable
private fun SongCarousel(
    title: String,
    songs: List<Song>,
    currentId: Long?,
    onPlaySong: (Song, List<Song>) -> Unit,
    onSeeAll: () -> Unit
) {
    Column {
        SectionHeader(title, Modifier.padding(start = 20.dp, end = 10.dp), actionLabel = "See all", onAction = onSeeAll)
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(songs, key = { it.id }) { song ->
                SongCard(song, onClick = { onPlaySong(song, songs) }, isCurrent = song.id == currentId)
            }
        }
    }
}

@Composable
fun OutputDevicePill(device: OutputDevice, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .glass(colors, RoundedCornerShape(20.dp), tint = if (device.type != AudioDeviceType.SPEAKER) colors.accent else null)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(device.type.icon(), null, tint = colors.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (device.type == AudioDeviceType.SPEAKER) "Playing on this phone" else "Connected · ${device.name}",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (device.supportsHiRes) {
            Spacer(Modifier.width(8.dp))
            Text("HI-RES", style = MaterialTheme.typography.labelSmall, color = colors.warning)
        }
    }
}

fun AudioDeviceType.icon(): ImageVector = when (this) {
    AudioDeviceType.BLUETOOTH -> Icons.Rounded.BluetoothAudio
    AudioDeviceType.WIRED_HEADSET -> Icons.Rounded.Headphones
    AudioDeviceType.USB -> Icons.Rounded.Usb
    AudioDeviceType.SPEAKER -> Icons.Rounded.PhoneAndroid
    AudioDeviceType.OTHER -> Icons.Rounded.Speaker
}

@Composable
private fun ContinueListeningCard(
    song: Song,
    playbackState: PlaybackState,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PulseTheme.colors
    val palette = remember(song.artworkKey()) { artworkPalette(song.artworkKey()) }
    val progress = if (playbackState.durationMs > 0) (playbackState.currentPositionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier
            .fillMaxWidth()
            .softGlow(palette.first(), radius = 32.dp, alpha = 0.4f * PulseTheme.motion)
            .clip(CardShape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(palette[0].copy(alpha = 0.55f), palette[1].copy(alpha = 0.30f), colors.surface.copy(alpha = 0.4f))
                )
            )
            .glass(colors, CardShape)
            .clickable(onClick = onOpen)
            .padding(18.dp)
    ) {
        Column {
            Text("Continue listening", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SongArtwork(song, Modifier.size(76.dp), RoundedCornerShape(18.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.titleLarge, color = if (colors.isDark) Color.White else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = if (colors.isDark) Color.White.copy(alpha = 0.7f) else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                GradientPlayButton(playbackState.isPlaying, onPlayPause, size = 56.dp)
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.18f))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.accentGradient)
                )
            }
        }
    }
}

@Composable
private fun StatsCard(stats: ListeningStats, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    GlassCard(modifier.fillMaxWidth(), onClick = onOpen, contentPadding = 18.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Listening stats", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.ChevronRight, null, tint = colors.textSecondary)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(0.42f)) {
                    Text("This week", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    Text(formatMinutes(stats.thisWeekMinutes), style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
                    val change = stats.weekChangePercent
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = colors.accentPartner, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (change != null) "${if (change >= 0) "+" else ""}$change% vs last week" else "${stats.currentStreakDays}-day streak",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )
                    }
                }
                if (stats.weeklyMinutes.all { it < 1f }) {
                    Text(
                        "Play a few songs and your week will show up here",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                        modifier = Modifier.weight(0.58f).padding(start = 12.dp, bottom = 4.dp)
                    )
                } else {
                    ListeningBarChart(
                        values = stats.weeklyMinutes,
                        labels = stats.weeklyLabels.map { it.take(1) },
                        height = 72.dp,
                        modifier = Modifier.weight(0.58f),
                        description = "Minutes listened in the last 7 days"
                    )
                }
            }
        }
    }
}

@Composable
private fun ThrowbackCard(songs: List<Song>, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PulseTheme.colors
    GlassCard(modifier.fillMaxWidth(), onClick = onPlay, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CollageArtwork(songs, "throwback", Modifier.size(84.dp), RoundedCornerShape(18.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("ON THIS DAY", style = MaterialTheme.typography.labelSmall, color = colors.accent)
                Text("Throwback mix", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
                Text(
                    songs.take(3).joinToString(" · ") { it.title },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            GlassIconButton(Icons.Rounded.PlayArrow, "Play throwback mix", onPlay, active = true)
        }
    }
}
