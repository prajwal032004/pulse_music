package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.scanner.LibraryData
import com.example.model.PlaybackState
import com.example.model.Song
import com.example.ui.components.*
import com.example.ui.theme.PulseTheme
import com.example.ui.viewmodel.DetailTarget

private enum class SearchFilter(val label: String) { ALL("All"), SONGS("Songs"), ALBUMS("Albums"), ARTISTS("Artists") }

@Composable
fun SearchScreen(
    library: LibraryData,
    playbackState: PlaybackState,
    recentSearches: List<String>,
    onPlaySong: (Song, List<Song>) -> Unit,
    onOpenDetail: (DetailTarget) -> Unit,
    onSubmitSearch: (String) -> Unit,
    onClearRecent: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false
) {
    val colors = PulseTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.ALL) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(autoFocus) { if (autoFocus) runCatching { focusRequester.requestFocus() } }

    val q = query.trim()
    val songs = remember(q, library.songs) {
        if (q.isEmpty()) emptyList() else library.songs.filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }.sortedByDescending { it.title.startsWith(q, true) }
    }
    val albums = remember(q, library.albums) { if (q.isEmpty()) emptyList() else library.albums.filter { it.title.contains(q, true) || it.artist.contains(q, true) } }
    val artists = remember(q, library.artists) { if (q.isEmpty()) emptyList() else library.artists.filter { it.name.contains(q, true) } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { ScreenHeader("Search") }
        item {
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(58.dp)
                    .softGlow(colors.accent, radius = 20.dp, cornerRadius = 29.dp, alpha = if (q.isNotEmpty()) 0.3f else 0f)
                    .glass(colors, RoundedCornerShape(29.dp), strong = true)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = colors.accent)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Songs, artists, albums…", style = MaterialTheme.typography.bodyLarge, color = colors.textTertiary)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSubmitSearch(query); focusManager.clearFocus() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("library_search_input")
                    )
                }
                if (query.isNotEmpty()) {
                    PulseIconButton(Icons.Rounded.Close, "Clear search", { query = "" }, tint = colors.textSecondary, iconSize = 20.dp)
                }
            }
        }

        if (q.isEmpty()) {
            if (recentSearches.isNotEmpty()) {
                item {
                    Column {
                        SectionHeader("Recent searches", Modifier.padding(start = 20.dp, end = 10.dp), actionLabel = "Clear all", onAction = onClearRecent)
                        Spacer(Modifier.height(10.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(recentSearches) { r -> PulseChip(r, false, { query = r }, icon = Icons.Rounded.History) }
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader("Browse genres")
                    Spacer(Modifier.height(12.dp))
                    library.genres.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                            row.forEach { genre ->
                                val palette = remember(genre.name) { artworkPalette("genre_${genre.name}") }
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .height(92.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(Brush.linearGradient(palette))
                                        .clickable { onOpenDetail(DetailTarget.GenreTarget(genre.name)) }
                                        .padding(16.dp)
                                ) {
                                    Text(genre.name, style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.align(Alignment.TopStart))
                                    Text("${(genre.songCount).plural("song")}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.align(Alignment.BottomStart))
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SearchFilter.entries) { f -> PulseChip(f.label, f == filter, { filter = f }) }
                }
            }
            if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.SearchOff,
                        "No results for \"$q\"",
                        "Check the spelling, or try an artist or album name instead."
                    )
                }
            } else {
                if (filter == SearchFilter.ALL && songs.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            SectionHeader("Top result")
                            Spacer(Modifier.height(10.dp))
                            val top = songs.first()
                            GlassCard(Modifier.fillMaxWidth(), onClick = { onSubmitSearch(query); onPlaySong(top, songs) }, tint = artworkPalette(top.artworkKey()).first()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SongArtwork(top, Modifier.size(72.dp), RoundedCornerShape(18.dp))
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(top.title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Song · ${top.artist}", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    GlassIconButton(Icons.Rounded.PlayArrow, "Play ${top.title}", { onSubmitSearch(query); onPlaySong(top, songs) }, active = true)
                                }
                            }
                        }
                    }
                }
                if ((filter == SearchFilter.ALL || filter == SearchFilter.SONGS) && songs.isNotEmpty()) {
                    item { SectionHeader("Songs", Modifier.padding(start = 20.dp, end = 10.dp), actionLabel = if (filter == SearchFilter.ALL && songs.size > 5) "See all" else null, onAction = { filter = SearchFilter.SONGS }) }
                    val shown = if (filter == SearchFilter.ALL) songs.drop(1).take(5) else songs
                    items(shown, key = { "s${it.id}" }) { s ->
                        SongRow(
                            s,
                            isCurrent = s.id == playbackState.currentSong?.id,
                            isPlaying = playbackState.isPlaying,
                            onClick = { onSubmitSearch(query); onPlaySong(s, songs) },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
                if ((filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS) && albums.isNotEmpty()) {
                    item {
                        Column {
                            SectionHeader("Albums", Modifier.padding(horizontal = 20.dp))
                            Spacer(Modifier.height(12.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(albums, key = { it.id }) { album ->
                                    Column(Modifier.width(140.dp).clip(RoundedCornerShape(20.dp)).clickable { onSubmitSearch(query); onOpenDetail(DetailTarget.AlbumTarget(album.title)) }) {
                                        ArtworkImage(album.artUri, album.title, Modifier.size(140.dp), RoundedCornerShape(20.dp), iconSize = 36.dp)
                                        Spacer(Modifier.height(8.dp))
                                        Text(album.title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(album.artist, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
                if ((filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS) && artists.isNotEmpty()) {
                    item {
                        Column {
                            SectionHeader("Artists", Modifier.padding(horizontal = 20.dp))
                            Spacer(Modifier.height(12.dp))
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(artists, key = { it.id }) { artist ->
                                    Column(
                                        Modifier.width(104.dp).clip(RoundedCornerShape(20.dp)).clickable { onSubmitSearch(query); onOpenDetail(DetailTarget.ArtistTarget(artist.name)) },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        ArtistAvatar(artist.name, Modifier.size(96.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Text(artist.name, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${(artist.songCount).plural("song")}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
