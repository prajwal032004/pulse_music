package com.example.smart

import com.example.data.MusicRepository
import com.example.model.Song
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar
import java.util.concurrent.TimeUnit

class SmartFeatureEngine(
    private val repository: MusicRepository
) {
    private val songs: List<Song> get() = repository.libraryData.value.songs

    suspend fun getSurpriseMeMix(): List<Song> = songs.shuffled().take(25)

    suspend fun getWorkoutMix(): List<Song> = mixByGenre("rock", "edm", "electronic", "hip-hop", "metal")

    suspend fun getChillEveningMix(): List<Song> = mixByGenre("jazz", "acoustic", "blues", "lofi", "ambient")

    suspend fun getFocusFlowMix(): List<Song> = mixByGenre("classical", "ambient", "acoustic", "instrumental")

    suspend fun getSleepMix(): List<Song> = mixByGenre("ambient", "classical", "acoustic", "jazz")

    private fun mixByGenre(vararg genres: String): List<Song> {
        val all = songs
        val matched = all.filter { song -> genres.any { song.genre.contains(it, ignoreCase = true) } }
        return if (matched.isNotEmpty()) matched.shuffled() else all.shuffled().take(20)
    }

    suspend fun getRecentlyPlayed(limit: Int = 20): List<Song> {
        val byId = songs.associateBy { it.id }
        val history = repository.recentHistory.firstOrNull().orEmpty()
        return history.map { it.songId }.distinct().mapNotNull { byId[it] }.take(limit)
    }

    suspend fun getMostPlayed(limit: Int = 25): List<Song> {
        val byId = songs.associateBy { it.id }
        return repository.getMostPlayedSongIds(limit).mapNotNull { byId[it] }
    }

    fun getRecentlyAdded(limit: Int = 50): List<Song> = songs.sortedByDescending { it.dateAdded }.take(limit)

    fun getFavorites(): List<Song> = songs.filter { it.isFavorite }

    /** "On this day" throwback: tracks played on this date in earlier weeks, else the oldest additions. */
    suspend fun getThrowbackTracks(): List<Song> {
        val byId = songs.associateBy { it.id }
        val today = Calendar.getInstance()
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(400)
        val sameWeekday = repository.getHistorySince(since).filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.playedAt }
            c.get(Calendar.DAY_OF_WEEK) == today.get(Calendar.DAY_OF_WEEK) &&
                System.currentTimeMillis() - it.playedAt > TimeUnit.DAYS.toMillis(6)
        }.map { it.songId }.distinct().mapNotNull { byId[it] }
        return sameWeekday.ifEmpty { songs.sortedBy { it.dateAdded }.take(10) }.take(10)
    }

    suspend fun getListeningStats(): ListeningStats {
        val now = System.currentTimeMillis()
        val history = repository.getHistorySince(now - TimeUnit.DAYS.toMillis(35))
        val byId = songs.associateBy { it.id }
        val dayMs = TimeUnit.DAYS.toMillis(1)

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Last 7 days, oldest first; minutes listened per day
        val weekly = (6 downTo 0).map { back ->
            val from = startOfToday - back * dayMs
            val to = from + dayMs
            history.filter { it.playedAt in from until to }.sumOf { it.durationPlayedMs } / 60_000f
        }
        val labels = (6 downTo 0).map { back ->
            val c = Calendar.getInstance().apply { timeInMillis = startOfToday - back * dayMs }
            c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, java.util.Locale.getDefault()) ?: ""
        }
        // Last 4 weeks, oldest first
        val monthly = (3 downTo 0).map { back ->
            val to = startOfToday + dayMs - back * 7 * dayMs
            val from = to - 7 * dayMs
            history.filter { it.playedAt in from until to }.sumOf { it.durationPlayedMs } / 60_000f
        }

        val playedDays = history.map { (it.playedAt - startOfToday).floorDiv(dayMs) }.toSet()
        var streak = 0
        var day = if (0L in playedDays) 0L else -1L
        while (day in playedDays) {
            streak++
            day--
        }

        val topArtists = history.groupingBy { it.artistName }.eachCount().entries
            .sortedByDescending { it.value }.take(5).map { it.key to it.value }
        val topSongs = history.groupingBy { it.songId }.eachCount().entries
            .sortedByDescending { it.value }.mapNotNull { e -> byId[e.key]?.let { it to e.value } }.take(5)
        val favoriteGenre = history.mapNotNull { byId[it.songId]?.genre }.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key ?: "—"

        val lastWeek = monthly.getOrElse(2) { 0f }
        return ListeningStats(
            totalPlays = history.size,
            totalMinutes = history.sumOf { it.durationPlayedMs } / 60_000L,
            topArtists = topArtists,
            topSongs = topSongs,
            currentStreakDays = streak,
            favoriteGenre = favoriteGenre,
            weeklyMinutes = weekly,
            weeklyLabels = labels,
            monthlyMinutes = monthly,
            thisWeekMinutes = weekly.sum(),
            lastWeekMinutes = lastWeek
        )
    }

    fun parseSmartCommand(command: String): SmartActionResult {
        val query = command.trim().lowercase()
        return when {
            query.contains("workout") -> SmartActionResult.PlayWorkoutMix
            query.contains("chill") || query.contains("relax") || query.contains("evening") -> SmartActionResult.PlayChillMix
            query.contains("focus") || query.contains("study") -> SmartActionResult.PlayFocusMix
            query.contains("surprise") || query.contains("random") -> SmartActionResult.PlaySurpriseMe
            query.contains("favorite") || query.contains("fav") -> SmartActionResult.PlayFavorites
            query.contains("sleep") -> {
                val minutes = Regex("(\\d+)").find(query)?.value?.toIntOrNull() ?: 20
                SmartActionResult.SetSleepTimer(minutes)
            }
            query.contains("bass") -> SmartActionResult.BoostBass
            query.contains("spatial") -> SmartActionResult.ToggleSpatialAudio
            query.contains("hearing") || query.contains("test") -> SmartActionResult.OpenHearingTest
            query.contains("equalizer") || query.contains("eq") -> SmartActionResult.OpenAudioLab
            query.contains("stats") || query.contains("review") -> SmartActionResult.ShowStats
            else -> SmartActionResult.SearchSong(command)
        }
    }
}

data class ListeningStats(
    val totalPlays: Int = 0,
    val totalMinutes: Long = 0,
    val topArtists: List<Pair<String, Int>> = emptyList(),
    val topSongs: List<Pair<Song, Int>> = emptyList(),
    val currentStreakDays: Int = 0,
    val favoriteGenre: String = "—",
    val weeklyMinutes: List<Float> = List(7) { 0f },
    val weeklyLabels: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    val monthlyMinutes: List<Float> = List(4) { 0f },
    val thisWeekMinutes: Float = 0f,
    val lastWeekMinutes: Float = 0f
) {
    val weekChangePercent: Int?
        get() = if (lastWeekMinutes > 1f) (((thisWeekMinutes - lastWeekMinutes) / lastWeekMinutes) * 100).toInt() else null
}

sealed interface SmartActionResult {
    object PlaySurpriseMe : SmartActionResult
    object PlayWorkoutMix : SmartActionResult
    object PlayChillMix : SmartActionResult
    object PlayFocusMix : SmartActionResult
    object PlayFavorites : SmartActionResult
    data class SetSleepTimer(val minutes: Int) : SmartActionResult
    object BoostBass : SmartActionResult
    object ToggleSpatialAudio : SmartActionResult
    object OpenHearingTest : SmartActionResult
    object OpenAudioLab : SmartActionResult
    object ShowStats : SmartActionResult
    data class SearchSong(val query: String) : SmartActionResult
}
