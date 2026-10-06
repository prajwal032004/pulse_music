package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.scanner.LibraryData
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.theme.PulseTheme
import com.example.ui.viewmodel.DetailTarget
import kotlinx.coroutines.launch

enum class LibraryTab(val title: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    GENRES("Genres"),
    FOLDERS("Folders"),
    YEARS("Years")
}

enum class SortOption(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    DATE_ADDED("Recently added"),
    DURATION("Duration"),
    YEAR("Year")
}

@Composable
fun LibraryScreen(
    library: LibraryData,
    playbackState: PlaybackState,
    onPlaySong: (Song, List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onOpenDetail: (DetailTarget) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val colors = PulseTheme.colors
    var tab by rememberSaveable { mutableStateOf(LibraryTab.SONGS) }
    var sort by rememberSaveable { mutableStateOf(SortOption.DATE_ADDED) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val songs = remember(library.songs, sort) {
        when (sort) {
            SortOption.TITLE -> library.songs.sortedBy { it.title.lowercase() }
            SortOption.ARTIST -> library.songs.sortedBy { it.artist.lowercase() }
            SortOption.DATE_ADDED -> library.songs.sortedByDescending { it.dateAdded }
            SortOption.DURATION -> library.songs.sortedByDescending { it.durationMs }
            SortOption.YEAR -> library.songs.sortedByDescending { it.year }
        }
    }
    val years = remember(library.songs) {
        library.songs.filter { it.year > 0 }.groupBy { it.year }.toSortedMap(compareByDescending { it })
    }

    Column(modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        ScreenHeader("Library", subtitle = "${(library.songs.size).plural("song")}") {
            GlassIconButton(Icons.Rounded.Shuffle, "Shuffle all", { onShuffleAll(library.songs) })
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(LibraryTab.entries) { t ->
                PulseChip(t.title, selected = t == tab, onClick = { tab = t }, modifier = Modifier.testTag("tab_${t.name.lowercase()}"))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .glass(colors, RoundedCornerShape(16.dp))
                        .clickable { sortMenu = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("sort_icon_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Sort, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sort: ${sort.label}", style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
                    Icon(Icons.Rounded.ExpandMore, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = sortMenu,
                    onDismissRequest = { sortMenu = false },
                    containerColor = colors.surfaceHigh,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    SortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label, color = if (option == sort) colors.accent else colors.textPrimary) },
                            trailingIcon = { if (option == sort) Icon(Icons.Rounded.Check, null, tint = colors.accent) },
                            onClick = { sort = option; sortMenu = false }
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            GlassIconButton(Icons.Rounded.ViewList, "List view", { grid = false }, size = 44.dp, active = !grid)
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Rounded.GridView, "Grid view", { grid = true }, size = 44.dp, active = grid)
        }
        Spacer(Modifier.height(8.dp))

        val bottom = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding())
        val gridBottom = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = contentPadding.calculateBottomPadding())
        when (tab) {
            LibraryTab.SONGS -> if (songs.isEmpty()) {
                EmptyState(Icons.Rounded.LibraryMusic, "No music yet", "Songs on your device will appear here after a scan.")
            } else if (grid) {
                LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = gridBottom, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    items(songs, key = { it.id }) { s ->
                        SongCard(s, { onPlaySong(s, songs) }, width = 160.dp, isCurrent = s.id == playbackState.currentSong?.id)
                    }
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = bottom) {
                        items(songs, key = { it.id }) { s ->
                            SongRow(
                                song = s,
                                isCurrent = s.id == playbackState.currentSong?.id,
                                isPlaying = playbackState.isPlaying,
                                onClick = { onPlaySong(s, songs) }
                            )
                        }
                    }
                    if (sort == SortOption.TITLE || sort == SortOption.ARTIST) {
                        val key: (Song) -> String = if (sort == SortOption.TITLE) { s -> s.title } else { s -> s.artist }
                        val letters = remember(songs, sort) { songs.map { key(it).firstOrNull()?.uppercaseChar() ?: '#' }.distinct() }
                        FastScrollIndex(
                            availableLetters = letters,
                            onLetterSelected = { c ->
                                val idx = songs.indexOfFirst {
                                    val first = key(it).firstOrNull()?.uppercaseChar() ?: '#'
                                    if (c == '#') !first.isLetter() else first >= c
                                }
                                if (idx >= 0) scope.launch { listState.scrollToItem(idx) }
                            },
                            modifier = Modifier.padding(end = 6.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding())
                        )
                    }
                }
            }

            LibraryTab.ALBUMS -> CollectionGrid(
                items = library.albums,
                grid = grid,
                padding = gridBottom,
                key = { it.id },
                title = { it.title },
                subtitle = { "${it.artist} · ${(it.songCount).plural("song")}" },
                art = { album, m -> ArtworkImage(album.artUri, album.title, m, RoundedCornerShape(20.dp), iconSize = 36.dp) },
                onClick = { onOpenDetail(DetailTarget.AlbumTarget(it.title)) }
            )

            LibraryTab.ARTISTS -> CollectionGrid(
                items = library.artists,
                grid = grid,
                padding = gridBottom,
                key = { it.id },
                title = { it.name },
                subtitle = { "${(it.albumCount).plural("album")} · ${(it.songCount).plural("song")}" },
                art = { artist, m -> ArtistAvatar(artist.name, m) },
                onClick = { onOpenDetail(DetailTarget.ArtistTarget(it.name)) },
                circular = true
            )

            LibraryTab.GENRES -> CollectionGrid(
                items = library.genres,
                grid = true,
                padding = gridBottom,
                key = { it.id },
                title = { it.name },
                subtitle = { "${(it.songCount).plural("song")}" },
                art = { genre, m -> ArtworkImage(null, "genre_${genre.name}", m, RoundedCornerShape(20.dp), iconSize = 36.dp) },
                onClick = { onOpenDetail(DetailTarget.GenreTarget(it.name)) }
            )

            LibraryTab.FOLDERS -> LazyColumn(contentPadding = gridBottom, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(library.folders, key = { it.path }) { folder ->
                    GlassCard(Modifier.fillMaxWidth(), onClick = { onOpenDetail(DetailTarget.FolderTarget(folder.path, folder.name)) }, contentPadding = 6.dp) {
                        GlassListRow(
                            Icons.Rounded.Folder,
                            folder.name,
                            subtitle = folder.path,
                            iconTint = colors.warning,
                            trailing = { Text("${folder.songCount}", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary) }
                        )
                    }
                }
            }

            LibraryTab.YEARS -> if (years.isEmpty()) {
                EmptyState(Icons.Rounded.CalendarMonth, "No release years", "Your tracks don't include year tags yet.")
            } else {
                LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = gridBottom, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(years.entries.toList(), key = { it.key }) { (year, list) ->
                        GlassCard(Modifier.fillMaxWidth().height(96.dp), onClick = { onOpenDetail(DetailTarget.YearTarget(year)) }) {
                            Column(Modifier.align(Alignment.BottomStart)) {
                                Text("$year", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
                                Text("${(list.size).plural("song")}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> CollectionGrid(
    items: List<T>,
    grid: Boolean,
    padding: PaddingValues,
    key: (T) -> Any,
    title: (T) -> String,
    subtitle: (T) -> String,
    art: @Composable (T, Modifier) -> Unit,
    onClick: (T) -> Unit,
    circular: Boolean = false
) {
    val colors = PulseTheme.colors
    if (grid) {
        LazyVerticalGrid(GridCells.Adaptive(150.dp), contentPadding = padding, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(items, key = key) { item ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onClick(item) },
                    horizontalAlignment = if (circular) Alignment.CenterHorizontally else Alignment.Start
                ) {
                    art(item, Modifier.fillMaxWidth().aspectRatio(1f))
                    Spacer(Modifier.height(8.dp))
                    Text(title(item), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle(item), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    } else {
        LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(items, key = key) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onClick(item) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    art(item, Modifier.size(60.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title(item), style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle(item), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = colors.textTertiary)
                }
            }
        }
    }
}

/** Initials avatar on the artist's generated gradient. */
@Composable
fun ArtistAvatar(name: String, modifier: Modifier = Modifier) {
    val palette = remember(name) { artworkPalette("artist_$name") }
    val initials = remember(name) {
        name.split(' ', '&', ',').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "♪" }
    }
    Box(
        modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(palette)),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, style = MaterialTheme.typography.headlineSmall, color = Color.White)
    }
}
