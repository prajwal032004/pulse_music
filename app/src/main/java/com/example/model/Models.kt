package com.example.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val albumArtUri: Uri? = null,
    val genre: String = "Unknown",
    val year: Int = 0,
    val trackNumber: Int = 0,
    val size: Long = 0,
    val dateAdded: Long = 0,
    val path: String = "",
    val mimeType: String = "audio/mpeg",
    val bitRate: Int = 0, // kbps, 0 when unknown
    val isHiRes: Boolean = false,
    val isLossless: Boolean = false,
    val isFavorite: Boolean = false,
    /** False when the file has no album tag and Android filled in the folder name instead. */
    val hasAlbumTag: Boolean = true
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artUri: Uri? = null,
    val songCount: Int = 0,
    val year: Int = 0
)

data class Artist(
    val id: Long,
    val name: String,
    val songCount: Int = 0,
    val albumCount: Int = 0
)

data class Genre(
    val id: Long,
    val name: String,
    val songCount: Int = 0
)

data class Folder(
    val path: String,
    val name: String,
    val songCount: Int = 0
)

data class CustomPlaylist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val isSmart: Boolean = false,
    val smartType: String = ""
)

enum class ShuffleMode {
    OFF, ON
}

enum class RepeatMode {
    OFF, ALL, ONE
}

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = -1,
    /** Queue indices in the order they will actually play next (respects shuffle). */
    val upNextIndices: List<Int> = emptyList(),
    val shuffleMode: ShuffleMode = ShuffleMode.OFF,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val sleepTimerRemainingSeconds: Long? = null,
    val sleepAtEndOfTrack: Boolean = false,
    val loopPointA: Long? = null,
    val loopPointB: Long? = null,
    val isSkipSilenceEnabled: Boolean = false
)

enum class AudioDeviceType(val label: String, val profileLabel: String) {
    SPEAKER("Phone Speaker", "Speaker"),
    WIRED_HEADSET("Wired Headphones", "Headphones"),
    BLUETOOTH("Bluetooth Audio", "Bluetooth"),
    USB("USB Audio DAC", "USB DAC"),
    OTHER("Audio Device", "Other")
}

/** A concrete audio output reported by AudioManager (name comes from the device itself). */
data class OutputDevice(
    val id: Int,
    val name: String,
    val type: AudioDeviceType,
    val maxSampleRateHz: Int = 0,
    val maxChannels: Int = 2,
    val supportsHiRes: Boolean = false,
    val isBle: Boolean = false
) {
    val detail: String
        get() = buildList {
            add(type.label)
            if (isBle) add("LE Audio")
            if (maxSampleRateHz > 0) add("${maxSampleRateHz / 1000} kHz")
            if (maxChannels > 2) add("${maxChannels}ch")
        }.joinToString(" · ")
}

data class EqBand(
    val index: Int,
    val centerFreqHz: Int,
    val levelMb: Int // millibels (-1500 to +1500 -> -15dB to +15dB)
) {
    val levelDb: Float get() = levelMb / 100f
    val displayFrequency: String
        get() = if (centerFreqHz >= 1000) "${centerFreqHz / 1000}K" else "$centerFreqHz"
}

enum class EqPreset(val displayName: String, val gainsDb: List<Float>) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    JAZZ("Jazz", listOf(3.5f, 2.5f, 1.0f, 1.5f, -1.5f, -1.5f, 0f, 2.0f, 3.0f, 3.5f)),
    ROCK("Rock", listOf(4.5f, 3.0f, -1.5f, -2.5f, -1.0f, 2.0f, 4.0f, 5.0f, 5.0f, 4.5f)),
    POP("Pop", listOf(-1.5f, -1.0f, 1.0f, 3.0f, 4.5f, 3.5f, 1.5f, -1.0f, -1.5f, -1.5f)),
    CLASSICAL("Classical", listOf(4.0f, 3.0f, 2.0f, 1.5f, -1.0f, -1.0f, 0f, 2.5f, 3.5f, 4.0f)),
    HIPHOP("Hip-Hop", listOf(6.0f, 5.0f, 2.0f, 1.0f, -1.0f, -1.0f, 1.5f, -1.0f, 2.5f, 3.5f)),
    EDM("EDM", listOf(5.5f, 4.5f, 1.5f, 0f, -2.0f, 1.5f, 3.5f, 4.5f, 5.0f, 4.5f)),
    METAL("Metal", listOf(5.0f, 4.0f, -1.0f, -2.0f, -0.5f, 2.5f, 4.5f, 5.5f, 5.0f, 4.0f)),
    ACOUSTIC("Acoustic", listOf(3.5f, 2.5f, 1.5f, 1.0f, 1.5f, 1.5f, 3.0f, 3.5f, 3.0f, 2.5f)),
    VOCAL("Vocal", listOf(-2.0f, -2.5f, -1.0f, 1.5f, 4.5f, 4.5f, 3.0f, 1.0f, -1.0f, -2.0f)),
    BASS_BOOST("Bass Boost", listOf(7.5f, 6.0f, 4.0f, 2.0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    PODCAST("Podcast", listOf(-3.0f, -1.5f, 0f, 3.0f, 5.0f, 4.0f, 2.0f, 0f, -2.0f, -3.0f)),
    CUSTOM("Custom", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f));

    companion object {
        fun fromName(name: String?): EqPreset? = entries.firstOrNull { it.name == name }
    }
}

data class HearingEarCurve(
    val gainsAtFreqs: Map<Int, Float> = mapOf(
        250 to 0f,
        500 to 0f,
        1000 to 0f,
        2000 to 0f,
        4000 to 0f,
        8000 to 0f
    )
)

data class AudioLabState(
    val equalizerEnabled: Boolean = true,
    val bands: List<EqBand> = default10Bands(),
    val preampGainDb: Float = 0f, // -12dB to +12dB
    val currentPreset: EqPreset = EqPreset.FLAT,
    val autoPresetByGenre: Boolean = false,
    val customBandsMb: List<Int> = List(10) { 0 },
    // Spatial Audio
    val spatialAudioEnabled: Boolean = true,
    val isHeadTrackingSupported: Boolean = false,
    val virtualSurroundEnabled: Boolean = false,
    val soundStage3D: Int = 40, // 0 to 100%
    val isSpatializerSupported: Boolean = true,
    val isDolbyAtmosDetected: Boolean = false,
    val isHiResDetected: Boolean = false,
    // Ear Controls
    val leftVolumePercent: Int = 100, // 0 to 100
    val rightVolumePercent: Int = 100, // 0 to 100
    val balance: Float = 0f, // -1.0 (Full Left) to +1.0 (Full Right)
    val isChannelSwapEnabled: Boolean = false,
    val isMonoModeEnabled: Boolean = false,
    val isHearingCompensationEnabled: Boolean = false,
    val leftHearingCurve: HearingEarCurve = HearingEarCurve(),
    val rightHearingCurve: HearingEarCurve = HearingEarCurve(),
    // Enhancement effects
    val bassBoostEnabled: Boolean = true,
    val bassBoostStrength: Int = 250, // 0 to 1000
    val loudnessEnabled: Boolean = false,
    val loudnessGainMb: Int = 200, // 0 to 1000 mB
    val isLimiterEnabled: Boolean = true,
    // Device Profile
    val currentDevice: AudioDeviceType = AudioDeviceType.SPEAKER,
    val outputDevice: OutputDevice? = null,
    val connectedOutputs: List<OutputDevice> = emptyList(),
    val deviceProfiles: Map<AudioDeviceType, EqPreset> = emptyMap(),
    val visualizerMagnitudes: List<Float> = List(32) { 0.1f }
) {
    companion object {
        val BAND_FREQUENCIES = listOf(32, 64, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

        fun default10Bands(): List<EqBand> {
            val freqs = BAND_FREQUENCIES
            return freqs.mapIndexed { index, freq ->
                EqBand(index = index, centerFreqHz = freq, levelMb = 0)
            }
        }
    }
}

data class LyricsLine(
    val timestampMs: Long,
    val text: String
)

data class LyricsData(
    val isSynced: Boolean = false,
    val lines: List<LyricsLine> = emptyList(),
    val plainText: String = "",
    val offsetMs: Long = 0L
)
