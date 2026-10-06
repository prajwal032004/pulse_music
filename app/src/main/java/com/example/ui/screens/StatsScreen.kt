package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.Song
import com.example.smart.ListeningStats
import com.example.ui.components.*
import com.example.ui.theme.PulseTheme
import java.util.Calendar

@Composable
fun StatsScreen(
    stats: ListeningStats,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = PulseTheme.colors
    AuroraBackground(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, "Back", onBack)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Your listening", style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
                    Text("Last 5 weeks", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }

            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.Schedule, formatMinutes(stats.totalMinutes.toFloat()), "Listening time", Modifier.weight(1f))
                StatTile(Icons.Rounded.PlayCircle, "${stats.totalPlays}", "Plays", Modifier.weight(1f))
                StatTile(Icons.Rounded.LocalFireDepartment, "${stats.currentStreakDays}d", "Streak", Modifier.weight(1f))
            }

            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 18.dp) {
                Column {
                    Text("This week", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Text(
                        formatMinutes(stats.thisWeekMinutes) + (stats.weekChangePercent?.let { "  ·  ${if (it >= 0) "+" else ""}$it% vs last week" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    ListeningBarChart(stats.weeklyMinutes, stats.weeklyLabels, height = 150.dp, description = "Minutes listened per day, last 7 days")
                }
            }

            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 18.dp) {
                Column {
                    Text("Last 4 weeks", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Spacer(Modifier.height(12.dp))
                    ListeningBarChart(stats.monthlyMinutes, listOf("3 wk ago", "2 wk ago", "Last wk", "This wk"), height = 130.dp, description = "Minutes listened per week, last 4 weeks")
                }
            }

            if (stats.topSongs.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 8.dp) {
                    Column {
                        Text("Top songs", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.padding(12.dp))
                        val songs = stats.topSongs.map { it.first }
                        stats.topSongs.forEachIndexed { i, (song, plays) ->
                            SongRow(
                                song, isCurrent = false, isPlaying = false,
                                onClick = { onPlaySong(song, songs) },
                                leading = { Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = colors.accent, modifier = Modifier.width(20.dp)) },
                                showDuration = false,
                                trailing = { Text("$plays plays", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.padding(end = 8.dp)) }
                            )
                        }
                    }
                }
            }

            if (stats.topArtists.isNotEmpty()) {
                GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = 16.dp) {
                    Column {
                        Text("Top artists", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                        Spacer(Modifier.height(12.dp))
                        stats.topArtists.forEachIndexed { i, (artist, plays) ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                ArtistAvatar(artist, Modifier.size(44.dp))
                                Spacer(Modifier.width(14.dp))
                                Text(artist, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("$plays plays", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                            }
                        }
                    }
                }
            }

            // Year in review
            GlassCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                tint = colors.accent,
                contentPadding = 20.dp
            ) {
                Column {
                    Text("${Calendar.getInstance().get(Calendar.YEAR)} IN REVIEW", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                    Text(
                        "Your sound so far",
                        style = MaterialTheme.typography.headlineMedium.copy(brush = Brush.linearGradient(colors.accentGradientColors)),
                        color = colors.textPrimary
                    )
                    Spacer(Modifier.height(12.dp))
                    ReviewLine("Top artist", stats.topArtists.firstOrNull()?.first ?: "Keep listening!")
                    ReviewLine("Top song", stats.topSongs.firstOrNull()?.first?.title ?: "—")
                    ReviewLine("Favorite genre", stats.favoriteGenre)
                    ReviewLine("Longest streak", "${stats.currentStreakDays} days")
                }
            }
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    val colors = PulseTheme.colors
    GlassCard(modifier, contentPadding = 14.dp) {
        Column {
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun ReviewLine(label: String, value: String) {
    val colors = PulseTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
