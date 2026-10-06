package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.CustomPlaylist
import com.example.model.Song
import com.example.ui.theme.PulseTheme
import kotlinx.coroutines.delay

/** Lets any song row open the shared song-actions sheet hosted by MainActivity. */
class SongActions(val onMore: (Song) -> Unit)

val LocalSongActions = staticCompositionLocalOf { SongActions {} }

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    showDuration: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = PulseTheme.colors
    val actions = LocalSongActions.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(if (isCurrent) Modifier.glass(colors, RoundedCornerShape(18.dp), tint = colors.accent) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("song_item_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Box(contentAlignment = Alignment.Center) {
            SongArtwork(song, Modifier.size(52.dp), RoundedCornerShape(14.dp), iconSize = 20.dp)
            if (isCurrent) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) { NowPlayingBars(playing = isPlaying, color = Color.White) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) colors.accent else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.isHiRes || song.isLossless) {
                    Icon(
                        Icons.Rounded.HighQuality,
                        contentDescription = if (song.isHiRes) "Hi-Res" else "Lossless",
                        tint = colors.warning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (showDuration) {
            Text(formatDuration(song.durationMs), style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
        }
        if (trailing != null) {
            trailing()
        } else {
            PulseIconButton(Icons.Rounded.MoreVert, "More options for ${song.title}", { actions.onMore(song) }, tint = colors.textSecondary, iconSize = 20.dp)
        }
    }
}

/** Square card used in horizontal carousels (Recently played, Most played…). */
@Composable
fun SongCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
    isCurrent: Boolean = false
) {
    val colors = PulseTheme.colors
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Box {
            SongArtwork(
                song,
                Modifier
                    .size(width)
                    .softGlow(artworkPalette(song.artworkKey()).first(), radius = 18.dp, cornerRadius = 20.dp, alpha = 0.3f * PulseTheme.motion),
                RoundedCornerShape(20.dp),
                iconSize = 36.dp
            )
            if (isCurrent) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accentGradient),
                    contentAlignment = Alignment.Center
                ) { NowPlayingBars(playing = true, color = Color.White, modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(song.title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    horizontalPadding: Dp = 20.dp,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = PulseTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

// ---------------------------------------------------------------------------------------------
// Sheets & dialogs
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PulseBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = PulseTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = if (colors.isDark) colors.surface else colors.surface,
        contentColor = colors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.textTertiary)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            content = content
        )
    }
}

@Composable
fun SongActionsSheet(
    song: Song,
    playlists: List<CustomPlaylist>,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: (CustomPlaylist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onGoToAlbum: () -> Unit,
    onGoToArtist: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit = {}
) {
    val colors = PulseTheme.colors
    var choosingPlaylist by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }

    PulseBottomSheet(onDismiss = onDismiss) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SongArtwork(song, Modifier.size(60.dp), RoundedCornerShape(16.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${song.artist} · ${song.album}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp, horizontal = 20.dp), color = colors.glassBorder)
        if (!choosingPlaylist) {
            Column(Modifier.padding(horizontal = 8.dp)) {
                GlassListRow(Icons.Rounded.QueuePlayNext, "Play next", onClick = { onPlayNext(); onDismiss() })
                GlassListRow(Icons.Rounded.AddToQueue, "Add to queue", onClick = { onAddToQueue(); onDismiss() })
                GlassListRow(Icons.Rounded.PlaylistAdd, "Add to playlist", onClick = { choosingPlaylist = true })
                GlassListRow(
                    if (song.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (song.isFavorite) "Remove from Liked Songs" else "Add to Liked Songs",
                    iconTint = colors.accentPartner,
                    onClick = { onToggleFavorite(); onDismiss() }
                )
                GlassListRow(Icons.Rounded.Album, "Go to album", subtitle = song.album, onClick = { onGoToAlbum(); onDismiss() })
                GlassListRow(Icons.Rounded.Person, "Go to artist", subtitle = song.artist, onClick = { onGoToArtist(); onDismiss() })
                GlassListRow(Icons.Rounded.IosShare, "Share", onClick = { onShare(); onDismiss() })
                GlassListRow(Icons.Rounded.Info, "Song details", subtitle = "Format, bitrate, file location", onClick = { onDetails(); onDismiss() })
            }
        } else {
            Column(Modifier.padding(horizontal = 8.dp)) {
                GlassListRow(Icons.Rounded.Add, "New playlist", iconTint = colors.accentPartner, onClick = { creating = true })
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(playlists, key = { it.id }) { pl ->
                        GlassListRow(Icons.Rounded.QueueMusic, pl.name, subtitle = "${(pl.songCount).plural("song")}", onClick = { onAddToPlaylist(pl); onDismiss() })
                    }
                }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "New playlist",
            placeholder = "My playlist",
            confirmLabel = "Create",
            onDismiss = { creating = false },
            onConfirm = { name -> onCreatePlaylist(name); creating = false; onDismiss() }
        )
    }
}

/** Glass dialog with a single text field (create / rename playlist, save queue). */
@Composable
fun TextInputDialog(
    title: String,
    placeholder: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    initial: String = ""
) {
    val colors = PulseTheme.colors
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .glass(colors, RoundedCornerShape(28.dp), strong = true)
                .background(colors.surface.copy(alpha = if (colors.isDark) 0.85f else 0.95f))
                .padding(24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder, color = colors.textTertiary) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onConfirm(text.trim()) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.glassBorder,
                    cursorColor = colors.accent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
                Spacer(Modifier.width(8.dp))
                GradientButton(confirmLabel, { if (text.isNotBlank()) onConfirm(text.trim()) }, height = 48.dp)
            }
        }
    }
}

/** Glass confirmation dialog for destructive actions. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = PulseTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .glass(colors, RoundedCornerShape(28.dp), strong = true)
                .background(colors.surface.copy(alpha = if (colors.isDark) 0.85f else 0.95f))
                .padding(24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Spacer(Modifier.height(10.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                )
                Spacer(Modifier.width(8.dp))
                GradientButton(confirmLabel, onConfirm, height = 48.dp)
            }
        }
    }
}

/** Floating glass toast shown above the mini player. */
@Composable
fun PulseToastHost(message: String?, modifier: Modifier = Modifier, onShown: () -> Unit) {
    val colors = PulseTheme.colors
    LaunchedEffect(message) {
        if (message != null) {
            delay(2600)
            onShown()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        var last by remember { mutableStateOf("") }
        if (message != null) last = message
        Row(
            Modifier
                .padding(horizontal = 24.dp)
                .softGlow(colors.accent, radius = 20.dp, alpha = 0.3f)
                .glass(colors, RoundedCornerShape(20.dp), strong = true)
                .background(colors.surface.copy(alpha = 0.7f))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.accentGradient)
            )
            Spacer(Modifier.width(12.dp))
            Text(last, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        }
    }
}
