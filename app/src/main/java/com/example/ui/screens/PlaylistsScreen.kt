package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.theme.PulseTheme
import com.example.ui.viewmodel.DetailTarget
import com.example.ui.viewmodel.PlaylistWithSongs
import com.example.ui.viewmodel.SmartPlaylistType

private data class SmartTile(val type: SmartPlaylistType, val icon: ImageVector, val gradient: List<Color>)

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistWithSongs>,
    likedCount: Int,
    onOpenDetail: (DetailTarget) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val colors = PulseTheme.colors
    var creating by remember { mutableStateOf(false) }
    val tiles = remember(colors.accent) {
        listOf(
            SmartTile(SmartPlaylistType.LIKED, Icons.Rounded.Favorite, listOf(colors.accentPartner, colors.accent)),
            SmartTile(SmartPlaylistType.MOST_PLAYED, Icons.Rounded.TrendingUp, listOf(Color(0xFFF97316), Color(0xFFDB2777))),
            SmartTile(SmartPlaylistType.RECENTLY_ADDED, Icons.Rounded.NewReleases, listOf(Color(0xFFF59E0B), Color(0xFFEA580C))),
            SmartTile(SmartPlaylistType.CHILL, Icons.Rounded.SelfImprovement, listOf(Color(0xFF06B6D4), Color(0xFF6366F1))),
            SmartTile(SmartPlaylistType.WORKOUT, Icons.Rounded.FitnessCenter, listOf(Color(0xFFEF4444), Color(0xFF7C3AED))),
            SmartTile(SmartPlaylistType.FOCUS, Icons.Rounded.Psychology, listOf(Color(0xFF10B981), Color(0xFF0EA5E9)))
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding()
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScreenHeader("Playlists", horizontalPadding = 0.dp) {
                GlassIconButton(Icons.Rounded.Add, "Create playlist", { creating = true }, active = true, modifier = Modifier.testTag("create_playlist_fab"))
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Smart playlists") }
        items(tiles, key = { it.type.name }) { tile ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .softGlow(tile.gradient.last(), radius = 18.dp, alpha = 0.3f * PulseTheme.motion)
                    .clip(CardShape)
                    .background(Brush.linearGradient(tile.gradient))
                    .glass(colors.copy(glassHighlight = Color.White.copy(alpha = 0.35f)), CardShape)
                    .clickable { onOpenDetail(DetailTarget.SmartTarget(tile.type)) }
                    .padding(16.dp)
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) { Icon(tile.icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                Column(Modifier.align(Alignment.BottomStart)) {
                    Text(tile.type.title, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                    Text(
                        if (tile.type == SmartPlaylistType.LIKED) "${likedCount.plural("song")}" else tile.type.subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionHeader("Your playlists", Modifier.padding(top = 10.dp))
        }
        if (playlists.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    Icons.Rounded.QueueMusic,
                    "No playlists yet",
                    "Create one here, or save your queue as a playlist from Now Playing.",
                    actionLabel = "Create playlist",
                    onAction = { creating = true }
                )
            }
        }
        items(playlists, key = { it.playlist.id }) { item ->
            Column(
                Modifier
                    .clip(CardShape)
                    .clickable { onOpenDetail(DetailTarget.PlaylistTarget(item.playlist)) }
            ) {
                CollageArtwork(item.songs, item.playlist.name, Modifier.fillMaxWidth().aspectRatio(1f), CardShape)
                Spacer(Modifier.height(8.dp))
                Text(item.playlist.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${(item.songs.size).plural("song")}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "New playlist",
            placeholder = "Late night drive",
            confirmLabel = "Create",
            onDismiss = { creating = false },
            onConfirm = { onCreatePlaylist(it); creating = false }
        )
    }
}
