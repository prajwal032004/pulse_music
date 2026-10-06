package com.example.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Album
import com.example.model.PlaybackState
import com.example.model.Song
import com.example.ui.components.*
import com.example.ui.theme.PulseTheme

@Composable
fun CollectionDetailScreen(
    title: String,
    subtitle: String,
    kindLabel: String,
    songs: List<Song>,
    playbackState: PlaybackState,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    isArtist: Boolean = false,
    albums: List<Album> = emptyList(),
    onOpenAlbum: (Album) -> Unit = {},
    onRemoveSong: ((Song) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onRename: ((String) -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 120.dp
) {
    val colors = PulseTheme.colors
    val coverKey = songs.firstOrNull()?.artworkKey() ?: title
    val palette = remember(coverKey) { artworkPalette(coverKey) }
    val totalMs = remember(songs) { songs.sumOf { it.durationMs } }
    var showMenu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
            item {
                Box(Modifier.fillMaxWidth().height(400.dp)) {
                    // Blurred artwork backdrop fading into the page
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(60.dp) else Modifier)
                            .background(Brush.verticalGradient(listOf(palette[0].copy(alpha = 0.7f), palette[1].copy(alpha = 0.35f), Color.Transparent)))
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.6f), colors.background)))
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val artModifier = Modifier
                            .size(196.dp)
                            .softGlow(palette.first(), radius = 40.dp, cornerRadius = if (isArtist) 98.dp else 28.dp, alpha = 0.6f * PulseTheme.motion)
                        if (isArtist) {
                            ArtistAvatar(title, artModifier)
                        } else if (songs.size >= 4 && kindLabel == "Playlist") {
                            CollageArtwork(songs, title, artModifier, RoundedCornerShape(28.dp))
                        } else {
                            ArtworkImage(songs.firstOrNull()?.albumArtUri, coverKey, artModifier, RoundedCornerShape(28.dp), iconSize = 56.dp)
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(kindLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.accent)
                        Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "$subtitle · ${(songs.size).plural("song")} · ${formatMinutes(totalMs / 60_000f)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GradientButton("Play", { onPlay(0) }, Modifier.weight(1f), icon = Icons.Rounded.PlayArrow)
                    Row(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .glass(colors, RoundedCornerShape(28.dp), strong = true)
                            .clickable(onClick = onShuffle),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(Icons.Rounded.Shuffle, null, tint = colors.accent)
                        Spacer(Modifier.width(10.dp))
                        Text("Shuffle", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    }
                    if (onDelete != null || onRename != null) {
                        GlassIconButton(Icons.Rounded.MoreHoriz, "Playlist options", { showMenu = true }, size = 56.dp, tint = colors.textSecondary)
                    }
                }
            }
            if (songs.isEmpty()) {
                item { EmptyState(Icons.Rounded.MusicOff, "Nothing here yet", "Add songs from any song's ⋮ menu.") }
            }
            itemsIndexed(songs, key = { i, s -> "${s.id}_$i" }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == playbackState.currentSong?.id,
                    isPlaying = playbackState.isPlaying,
                    onClick = { onPlay(index) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                    leading = if (kindLabel == "Album") {
                        { Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = colors.textTertiary, modifier = Modifier.width(22.dp), textAlign = TextAlign.Center) }
                    } else null,
                    trailing = if (onRemoveSong != null) {
                        { PulseIconButton(Icons.Rounded.RemoveCircleOutline, "Remove ${song.title}", { onRemoveSong(song) }, tint = colors.textSecondary, iconSize = 20.dp) }
                    } else null
                )
            }
            if (isArtist && albums.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 24.dp)) {
                        SectionHeader("Albums", Modifier.padding(horizontal = 24.dp))
                        Spacer(Modifier.height(12.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(albums, key = { it.id }) { album ->
                                Column(Modifier.width(150.dp).clip(RoundedCornerShape(20.dp)).clickable { onOpenAlbum(album) }) {
                                    ArtworkImage(album.artUri, album.title, Modifier.size(150.dp), RoundedCornerShape(20.dp), iconSize = 36.dp)
                                    Spacer(Modifier.height(8.dp))
                                    Text(album.title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (album.year > 0) "${album.year}" else "${(album.songCount).plural("song")}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showMenu) {
            PulseBottomSheet(onDismiss = { showMenu = false }) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                Column(Modifier.padding(horizontal = 8.dp)) {
                    if (onRename != null) GlassListRow(Icons.Rounded.Edit, "Rename playlist", onClick = { showMenu = false; renaming = true })
                    if (onDelete != null) GlassListRow(Icons.Rounded.DeleteOutline, "Delete playlist", subtitle = "Songs stay on your phone", iconTint = colors.accentPartner, onClick = { showMenu = false; confirmDelete = true })
                }
            }
        }
        if (renaming && onRename != null) {
            TextInputDialog(
                title = "Rename playlist",
                placeholder = title,
                confirmLabel = "Rename",
                initial = title,
                onDismiss = { renaming = false },
                onConfirm = { onRename(it); renaming = false }
            )
        }
        if (confirmDelete && onDelete != null) {
            ConfirmDialog(
                title = "Delete \"$title\"?",
                message = "The playlist will be removed. Your music files are not touched.",
                confirmLabel = "Delete",
                onDismiss = { confirmDelete = false },
                onConfirm = { confirmDelete = false; onDelete() }
            )
        }

        GlassIconButton(
            Icons.Rounded.ArrowBack,
            "Back",
            onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
        )
    }
}
