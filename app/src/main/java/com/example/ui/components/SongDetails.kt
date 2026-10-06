package com.example.ui.components

import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Song
import com.example.ui.theme.PulseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Technical details read from the file itself (not guessed). */
private data class FileInfo(val bitrateKbps: Int?, val sampleRateHz: Int?, val bitsPerSample: Int?, val channels: String?)

@Composable
fun SongDetailsSheet(song: Song, loadPlayCount: suspend () -> Int, onDismiss: () -> Unit) {
    val colors = PulseTheme.colors
    val context = LocalContext.current
    val info by produceState<FileInfo?>(null, song.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, song.uri)
                    fun key(k: Int) = r.extractMetadata(k)?.toIntOrNull()
                    FileInfo(
                        bitrateKbps = key(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.div(1000),
                        sampleRateHz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) key(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE) else null,
                        bitsPerSample = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) key(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE) else null,
                        channels = null
                    )
                } finally {
                    r.release()
                }
            }.getOrNull() ?: FileInfo(null, null, null, null)
        }
    }
    val plays by produceState<Int?>(null, song.id) { value = runCatching { loadPlayCount() }.getOrNull() }

    val format = song.path.substringAfterLast('.', "").uppercase().ifBlank { song.mimeType.substringAfter('/').uppercase() }
    // Prefer the true average (size / duration); the decoder header value is unreliable for VBR files.
    val bitrate = song.bitRate.takeIf { it > 0 } ?: info?.bitrateKbps?.takeIf { it > 0 }
    val quality = when {
        song.isHiRes -> "Hi-Res lossless"
        song.isLossless -> "Lossless"
        bitrate != null && bitrate >= 256 -> "High quality"
        bitrate != null -> "Standard"
        else -> null
    }

    PulseBottomSheet(onDismiss = onDismiss) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SongArtwork(song, Modifier.size(64.dp), RoundedCornerShape(16.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Column(
            Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Headline audio stats
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Icons.Rounded.AudioFile, format.ifBlank { "—" }, "Format", Modifier.weight(1f))
                StatTile(Icons.Rounded.Speed, bitrate?.let { "$it kbps" } ?: "…", "Avg bitrate", Modifier.weight(1f))
                StatTile(
                    Icons.Rounded.GraphicEq,
                    info?.sampleRateHz?.let { "%.1f kHz".format(Locale.US, it / 1000f).replace(".0 ", " ") } ?: if (info == null) "…" else "—",
                    "Sample rate",
                    Modifier.weight(1f)
                )
            }
            GlassCard(Modifier.fillMaxWidth(), contentPadding = 6.dp) {
                Column {
                    if (quality != null) DetailRow(Icons.Rounded.HighQuality, "Quality", quality + (info?.bitsPerSample?.takeIf { it > 0 }?.let { " · $it-bit" } ?: ""))
                    DetailRow(Icons.Rounded.Timer, "Duration", formatDuration(song.durationMs))
                    DetailRow(Icons.Rounded.SdStorage, "File size", formatBytes(song.size))
                    DetailRow(Icons.Rounded.Album, "Album", song.album)
                    DetailRow(Icons.Rounded.Category, "Genre", song.genre)
                    if (song.year > 0) DetailRow(Icons.Rounded.CalendarMonth, "Year", song.year.toString())
                    if (song.trackNumber > 0) DetailRow(Icons.Rounded.Tag, "Track", song.trackNumber.toString())
                    if (song.dateAdded > 0) DetailRow(Icons.Rounded.Download, "Added", DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(song.dateAdded)))
                    DetailRow(Icons.Rounded.Headphones, "Plays", plays?.toString() ?: "…")
                }
            }
            if (song.path.isNotBlank()) {
                GlassCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                    Column {
                        Text("FILE LOCATION", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        SelectionContainer {
                            Text(song.path, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    val colors = PulseTheme.colors
    GlassCard(modifier, contentPadding = 12.dp, tint = colors.accent) {
        Column {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    val colors = PulseTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = colors.textTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "—"
    bytes < 1024 * 1024 -> "%.0f KB".format(Locale.US, bytes / 1024f)
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(Locale.US, bytes / (1024f * 1024f))
    else -> "%.2f GB".format(Locale.US, bytes / (1024f * 1024f * 1024f))
}
