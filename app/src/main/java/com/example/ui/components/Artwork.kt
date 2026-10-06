package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.model.Song
import com.example.ui.theme.ArtworkPalettes
import kotlin.math.abs

/** Stable palette for a track/album so generated covers stay consistent everywhere. */
fun artworkPalette(key: String): List<Color> = ArtworkPalettes[abs(key.hashCode()) % ArtworkPalettes.size]

fun Song.artworkKey(): String = if (hasAlbumTag && album.isNotBlank()) album else "$title·$artist"

/**
 * Album artwork with a generated gradient cover underneath. When the real art loads it covers
 * the gradient; when it is missing or fails, the gradient cover remains.
 */
@Composable
fun ArtworkImage(
    model: Any?,
    key: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    contentDescription: String? = null,
    iconSize: Dp = 28.dp
) {
    val palette = remember(key) { artworkPalette(key) }
    Box(
        modifier = modifier.clip(shape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.linearGradient(palette, start = Offset.Zero, end = Offset(size.width, size.height)))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width * 0.72f, size.height * 0.28f),
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.72f, size.height * 0.28f)
            )
            val ringCenter = Offset(size.width * 0.5f, size.height * 1.05f)
            for (i in 1..4) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.07f),
                    radius = size.minDimension * 0.22f * i,
                    center = ringCenter,
                    style = Stroke(width = size.minDimension * 0.012f)
                )
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startY = size.height * 0.55f))
        }
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(iconSize)
        )
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun SongArtwork(
    song: Song,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    iconSize: Dp = 24.dp
) {
    ArtworkImage(
        model = song.albumArtUri,
        key = song.artworkKey(),
        modifier = modifier,
        shape = shape,
        contentDescription = "${song.title} artwork",
        iconSize = iconSize
    )
}

/** 2×2 collage cover for playlists, falling back to one generated cover. */
@Composable
fun CollageArtwork(
    songs: List<Song>,
    fallbackKey: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp)
) {
    val distinct = remember(songs) { songs.distinctBy { it.artworkKey() }.take(4) }
    if (distinct.size < 4) {
        ArtworkImage(
            model = distinct.firstOrNull()?.albumArtUri,
            key = distinct.firstOrNull()?.artworkKey() ?: fallbackKey,
            modifier = modifier,
            shape = shape,
            iconSize = 36.dp
        )
        return
    }
    Column(modifier.clip(shape).background(Color.Black)) {
        for (row in 0..1) {
            Row(Modifier.weight(1f)) {
                for (col in 0..1) {
                    val song = distinct[row * 2 + col]
                    SongArtwork(
                        song = song,
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        iconSize = 18.dp
                    )
                }
            }
        }
    }
}
