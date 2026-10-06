package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_music_prefs")

/** User-facing app settings shown in Settings and read by playback/headset automation. */
data class AppSettings(
    val themeMode: String = "AMOLED",
    val accent: String = "VIOLET",
    val animationIntensity: Float = 1.0f,
    val skipSilence: Boolean = false,
    val pauseOnDisconnect: Boolean = true,
    val resumeOnReconnect: Boolean = true,
    val bluetoothAutoplay: Boolean = false,
    val perDeviceEq: Boolean = true,
    val sleepFadeOut: Boolean = true,
    val lyricsFontScale: Float = 1.0f,
    val onboardingDone: Boolean = false,
    val shakeToSkip: Boolean = false,
    val keepScreenOn: Boolean = false,
    /** Audio shorter than this (seconds) is hidden from the library. */
    val minTrackSeconds: Int = 20
)

/** Persisted Audio Lab state restored on launch. */
data class SavedAudioSettings(
    val eqEnabled: Boolean,
    val preset: String?,
    val bandsMb: List<Int>?,
    val customBandsMb: List<Int>?,
    val preampDb: Float,
    val bassBoost: Int,
    val bassBoostEnabled: Boolean,
    val virtualizer: Int,
    val virtualizerEnabled: Boolean,
    val loudnessGain: Int,
    val loudnessEnabled: Boolean,
    val spatial: Boolean,
    val mono: Boolean,
    val swap: Boolean,
    val balance: Float,
    val leftVolume: Int,
    val rightVolume: Int,
    val autoPresetByGenre: Boolean,
    val deviceProfiles: Map<String, String>
)

class PreferencesManager(private val context: Context) {

    private object Keys {
        val LAST_PLAYED_SONG_ID = longPreferencesKey("last_played_song_id")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val LAST_QUEUE_IDS = stringPreferencesKey("last_queue_ids")
        val SHUFFLE_MODE = stringPreferencesKey("shuffle_mode")
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val EQ_CUSTOM_BANDS = stringPreferencesKey("eq_custom_bands")
        val PREAMP = floatPreferencesKey("preamp_db")
        val BASS_BOOST = intPreferencesKey("bass_boost")
        val BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
        val VIRTUALIZER_ENABLED = booleanPreferencesKey("virtualizer_enabled")
        val LOUDNESS_GAIN = intPreferencesKey("loudness_gain")
        val LOUDNESS_ENABLED = booleanPreferencesKey("loudness_enabled")
        val SPATIAL_AUDIO = booleanPreferencesKey("spatial_audio")
        val MONO_AUDIO = booleanPreferencesKey("mono_audio")
        val CHANNEL_SWAP = booleanPreferencesKey("channel_swap")
        val BALANCE = floatPreferencesKey("audio_balance")
        val LEFT_VOLUME = intPreferencesKey("left_volume")
        val RIGHT_VOLUME = intPreferencesKey("right_volume")
        val AUTO_PRESET_GENRE = booleanPreferencesKey("auto_preset_genre")
        val DEVICE_PROFILES = stringPreferencesKey("device_eq_profiles")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = stringPreferencesKey("accent_color")
        val ANIMATION_INTENSITY = floatPreferencesKey("animation_intensity")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val PAUSE_ON_DISCONNECT = booleanPreferencesKey("pause_on_disconnect")
        val RESUME_ON_RECONNECT = booleanPreferencesKey("resume_on_reconnect")
        val BT_AUTOPLAY = booleanPreferencesKey("bt_autoplay")
        val PER_DEVICE_EQ = booleanPreferencesKey("per_device_eq")
        val SLEEP_FADE = booleanPreferencesKey("sleep_fade")
        val LYRICS_FONT_SCALE = floatPreferencesKey("lyrics_font_scale")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val RECENT_SEARCHES = stringPreferencesKey("recent_searches")
        val SHAKE_TO_SKIP = booleanPreferencesKey("shake_to_skip")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val MIN_TRACK_SECONDS = intPreferencesKey("min_track_seconds")
        val PLAYBACK_PITCH = floatPreferencesKey("playback_pitch")
    }

    private val data: Flow<Preferences> get() = context.dataStore.data.catchHandle()

    val lastSongId: Flow<Long?> = data.map { it[Keys.LAST_PLAYED_SONG_ID] }
    val lastPositionMs: Flow<Long> = data.map { it[Keys.LAST_POSITION_MS] ?: 0L }
    val shuffleMode: Flow<String> = data.map { it[Keys.SHUFFLE_MODE] ?: "OFF" }
    val repeatMode: Flow<String> = data.map { it[Keys.REPEAT_MODE] ?: "OFF" }
    val playbackSpeed: Flow<Float> = data.map { it[Keys.PLAYBACK_SPEED] ?: 1.0f }
    val playbackPitch: Flow<Float> = data.map { it[Keys.PLAYBACK_PITCH] ?: 1.0f }
    val minTrackSeconds: Flow<Int> = data.map { it[Keys.MIN_TRACK_SECONDS] ?: 20 }
    val themeMode: Flow<String> = data.map { it[Keys.THEME_MODE] ?: "AMOLED" }
    val animationIntensity: Flow<Float> = data.map { it[Keys.ANIMATION_INTENSITY] ?: 1.0f }
    val isSkipSilence: Flow<Boolean> = data.map { it[Keys.SKIP_SILENCE] ?: false }

    val recentSearches: Flow<List<String>> = data.map { prefs ->
        prefs[Keys.RECENT_SEARCHES]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }

    val settings: Flow<AppSettings> = data.map { p ->
        AppSettings(
            themeMode = p[Keys.THEME_MODE] ?: "AMOLED",
            accent = p[Keys.ACCENT] ?: "VIOLET",
            animationIntensity = p[Keys.ANIMATION_INTENSITY] ?: 1.0f,
            skipSilence = p[Keys.SKIP_SILENCE] ?: false,
            pauseOnDisconnect = p[Keys.PAUSE_ON_DISCONNECT] ?: true,
            resumeOnReconnect = p[Keys.RESUME_ON_RECONNECT] ?: true,
            bluetoothAutoplay = p[Keys.BT_AUTOPLAY] ?: false,
            perDeviceEq = p[Keys.PER_DEVICE_EQ] ?: true,
            sleepFadeOut = p[Keys.SLEEP_FADE] ?: true,
            lyricsFontScale = p[Keys.LYRICS_FONT_SCALE] ?: 1.0f,
            onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            shakeToSkip = p[Keys.SHAKE_TO_SKIP] ?: false,
            keepScreenOn = p[Keys.KEEP_SCREEN_ON] ?: false,
            minTrackSeconds = p[Keys.MIN_TRACK_SECONDS] ?: 20
        )
    }

    suspend fun lastQueueIds(): List<Long> =
        data.first()[Keys.LAST_QUEUE_IDS]?.split(',')?.mapNotNull { it.toLongOrNull() } ?: emptyList()

    suspend fun savePlaybackState(songId: Long, positionMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_PLAYED_SONG_ID] = songId
            prefs[Keys.LAST_POSITION_MS] = positionMs
        }
    }

    suspend fun saveQueue(songIds: List<Long>) {
        context.dataStore.edit { it[Keys.LAST_QUEUE_IDS] = songIds.take(500).joinToString(",") }
    }

    suspend fun saveShuffleMode(mode: String) = context.dataStore.edit { it[Keys.SHUFFLE_MODE] = mode }
    suspend fun saveRepeatMode(mode: String) = context.dataStore.edit { it[Keys.REPEAT_MODE] = mode }
    suspend fun savePlaybackSpeed(speed: Float) = context.dataStore.edit { it[Keys.PLAYBACK_SPEED] = speed }
    suspend fun saveThemeMode(theme: String) = context.dataStore.edit { it[Keys.THEME_MODE] = theme }
    suspend fun saveAccent(accent: String) = context.dataStore.edit { it[Keys.ACCENT] = accent }
    suspend fun saveAnimationIntensity(intensity: Float) = context.dataStore.edit { it[Keys.ANIMATION_INTENSITY] = intensity }
    suspend fun saveSkipSilence(enabled: Boolean) = context.dataStore.edit { it[Keys.SKIP_SILENCE] = enabled }
    suspend fun savePauseOnDisconnect(enabled: Boolean) = context.dataStore.edit { it[Keys.PAUSE_ON_DISCONNECT] = enabled }
    suspend fun saveResumeOnReconnect(enabled: Boolean) = context.dataStore.edit { it[Keys.RESUME_ON_RECONNECT] = enabled }
    suspend fun saveBluetoothAutoplay(enabled: Boolean) = context.dataStore.edit { it[Keys.BT_AUTOPLAY] = enabled }
    suspend fun savePerDeviceEq(enabled: Boolean) = context.dataStore.edit { it[Keys.PER_DEVICE_EQ] = enabled }
    suspend fun saveSleepFade(enabled: Boolean) = context.dataStore.edit { it[Keys.SLEEP_FADE] = enabled }
    suspend fun saveLyricsFontScale(scale: Float) = context.dataStore.edit { it[Keys.LYRICS_FONT_SCALE] = scale }
    suspend fun saveOnboardingDone() = context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    suspend fun savePlaybackPitch(pitch: Float) = context.dataStore.edit { it[Keys.PLAYBACK_PITCH] = pitch }
    suspend fun saveShakeToSkip(enabled: Boolean) = context.dataStore.edit { it[Keys.SHAKE_TO_SKIP] = enabled }
    suspend fun saveKeepScreenOn(enabled: Boolean) = context.dataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    suspend fun saveMinTrackSeconds(seconds: Int) = context.dataStore.edit { it[Keys.MIN_TRACK_SECONDS] = seconds }

    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.RECENT_SEARCHES]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(trimmed) + existing.filterNot { it.equals(trimmed, ignoreCase = true) }).take(10)
            prefs[Keys.RECENT_SEARCHES] = updated.joinToString("\n")
        }
    }

    suspend fun clearRecentSearches() = context.dataStore.edit { it.remove(Keys.RECENT_SEARCHES) }

    suspend fun loadAudioSettings(): SavedAudioSettings {
        val p = data.first()
        return SavedAudioSettings(
            eqEnabled = p[Keys.EQ_ENABLED] ?: true,
            preset = p[Keys.EQ_PRESET],
            bandsMb = p[Keys.EQ_BANDS]?.split(',')?.mapNotNull { it.toIntOrNull() },
            customBandsMb = p[Keys.EQ_CUSTOM_BANDS]?.split(',')?.mapNotNull { it.toIntOrNull() },
            preampDb = p[Keys.PREAMP] ?: 0f,
            bassBoost = p[Keys.BASS_BOOST] ?: 250,
            bassBoostEnabled = p[Keys.BASS_BOOST_ENABLED] ?: true,
            virtualizer = p[Keys.VIRTUALIZER_STRENGTH] ?: 40,
            virtualizerEnabled = p[Keys.VIRTUALIZER_ENABLED] ?: false,
            loudnessGain = p[Keys.LOUDNESS_GAIN] ?: 200,
            loudnessEnabled = p[Keys.LOUDNESS_ENABLED] ?: false,
            spatial = p[Keys.SPATIAL_AUDIO] ?: true,
            mono = p[Keys.MONO_AUDIO] ?: false,
            swap = p[Keys.CHANNEL_SWAP] ?: false,
            balance = p[Keys.BALANCE] ?: 0f,
            leftVolume = p[Keys.LEFT_VOLUME] ?: 100,
            rightVolume = p[Keys.RIGHT_VOLUME] ?: 100,
            autoPresetByGenre = p[Keys.AUTO_PRESET_GENRE] ?: false,
            deviceProfiles = p[Keys.DEVICE_PROFILES]?.split(';')?.mapNotNull { entry ->
                val parts = entry.split('=')
                if (parts.size == 2) parts[0] to parts[1] else null
            }?.toMap() ?: emptyMap()
        )
    }

    suspend fun saveAudioSettings(s: SavedAudioSettings) {
        context.dataStore.edit { p ->
            p[Keys.EQ_ENABLED] = s.eqEnabled
            if (s.preset != null) p[Keys.EQ_PRESET] = s.preset
            s.bandsMb?.let { p[Keys.EQ_BANDS] = it.joinToString(",") }
            s.customBandsMb?.let { p[Keys.EQ_CUSTOM_BANDS] = it.joinToString(",") }
            p[Keys.PREAMP] = s.preampDb
            p[Keys.BASS_BOOST] = s.bassBoost
            p[Keys.BASS_BOOST_ENABLED] = s.bassBoostEnabled
            p[Keys.VIRTUALIZER_STRENGTH] = s.virtualizer
            p[Keys.VIRTUALIZER_ENABLED] = s.virtualizerEnabled
            p[Keys.LOUDNESS_GAIN] = s.loudnessGain
            p[Keys.LOUDNESS_ENABLED] = s.loudnessEnabled
            p[Keys.SPATIAL_AUDIO] = s.spatial
            p[Keys.MONO_AUDIO] = s.mono
            p[Keys.CHANNEL_SWAP] = s.swap
            p[Keys.BALANCE] = s.balance
            p[Keys.LEFT_VOLUME] = s.leftVolume
            p[Keys.RIGHT_VOLUME] = s.rightVolume
            p[Keys.AUTO_PRESET_GENRE] = s.autoPresetByGenre
            p[Keys.DEVICE_PROFILES] = s.deviceProfiles.entries.joinToString(";") { "${it.key}=${it.value}" }
        }
    }

    private fun Flow<Preferences>.catchHandle(): Flow<Preferences> = catch { exception ->
        if (exception is IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }
}
