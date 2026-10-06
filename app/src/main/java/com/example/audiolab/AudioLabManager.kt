package com.example.audiolab

import android.content.Context
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import com.example.data.PreferencesManager
import com.example.data.SavedAudioSettings
import com.example.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.ln
import kotlin.math.sin

class AudioLabManager(
    context: Context,
    private val preferencesManager: PreferencesManager,
    private val deviceMonitor: AudioDeviceMonitor,
    private val earProcessor: EarControlAudioProcessor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentAudioSessionId: Int = 0

    private val _audioLabState = MutableStateFlow(AudioLabState())
    val audioLabState: StateFlow<AudioLabState> = _audioLabState.asStateFlow()

    /** Live spectrum bar heights (0..1), kept separate so 70ms updates don't recompose everything. */
    private val _spectrum = MutableStateFlow(List(32) { 0.06f })
    val spectrum: StateFlow<List<Float>> = _spectrum.asStateFlow()

    /** Emits a short message when a device profile is auto-applied (shown as an in-app toast). */
    private val _profileEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val profileEvents: SharedFlow<String> = _profileEvents.asSharedFlow()

    private var perDeviceEqEnabled = true
    private var isPlaying = false
    private var restored = false
    val toneGenerator = ToneGenerator()

    init {
        detectAudioHardwareCapabilities()
        observeDevices()
        startSpectrumVisualizer()
        scope.launch {
            restoreSavedSettings()
            restored = true
            observeAndPersist()
        }
        scope.launch {
            preferencesManager.settings.map { it.perDeviceEq }.distinctUntilChanged().collect { perDeviceEqEnabled = it }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Session & effects
    // -----------------------------------------------------------------------------------------

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == currentAudioSessionId) return
        currentAudioSessionId = audioSessionId
        releaseAudioEffects()

        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()
        loudnessEnhancer = runCatching { LoudnessEnhancer(audioSessionId) }.getOrNull()
        applyAllSettings()
    }

    fun setPlaying(playing: Boolean) {
        isPlaying = playing
    }

    private fun detectAudioHardwareCapabilities() {
        var spatialSupported = false
        var headTracking = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            runCatching {
                val spatializer = audioManager.spatializer
                spatialSupported = spatializer.immersiveAudioLevel != android.media.Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    headTracking = spatializer.isHeadTrackerAvailable
                }
            }
        }
        _audioLabState.update {
            it.copy(
                isSpatializerSupported = spatialSupported,
                isHeadTrackingSupported = headTracking,
                isDolbyAtmosDetected = checkDolbyAtmosSupport()
            )
        }
    }

    private fun checkDolbyAtmosSupport(): Boolean = runCatching {
        audioManager.getProperty("ro.audio.soundfx.type")?.contains("dolby", ignoreCase = true) == true
    }.getOrDefault(false)

    // -----------------------------------------------------------------------------------------
    // Headphones & Bluetooth
    // -----------------------------------------------------------------------------------------

    private fun observeDevices() {
        scope.launch {
            combine(deviceMonitor.currentOutput, deviceMonitor.connectedOutputs) { current, all -> current to all }
                .collect { (current, all) ->
                    val previousType = _audioLabState.value.currentDevice
                    _audioLabState.update {
                        it.copy(currentDevice = current.type, outputDevice = current, connectedOutputs = all)
                    }
                    if (restored && previousType != current.type) applyDeviceProfile(current)
                }
        }
    }

    private fun applyDeviceProfile(device: OutputDevice) {
        if (!perDeviceEqEnabled) return
        val preset = _audioLabState.value.deviceProfiles[device.type] ?: return
        applyPreset(preset)
        _profileEvents.tryEmit("${device.name} · ${preset.displayName} EQ applied")
    }

    /** Remembers the current EQ as the profile for the given output type. */
    fun saveProfileFor(type: AudioDeviceType) {
        val preset = _audioLabState.value.currentPreset
        _audioLabState.update { it.copy(deviceProfiles = it.deviceProfiles + (type to preset)) }
    }

    fun clearProfileFor(type: AudioDeviceType) {
        _audioLabState.update { it.copy(deviceProfiles = it.deviceProfiles - type) }
    }

    // -----------------------------------------------------------------------------------------
    // Equalizer
    // -----------------------------------------------------------------------------------------

    fun setEqualizerEnabled(enabled: Boolean) {
        _audioLabState.update { it.copy(equalizerEnabled = enabled) }
        runCatching { equalizer?.enabled = enabled }
        pushEarSettings()
    }

    fun setBandLevel(bandIndex: Int, levelMb: Int) {
        _audioLabState.update { current ->
            val updated = current.bands.map { if (it.index == bandIndex) it.copy(levelMb = levelMb.coerceIn(-1500, 1500)) else it }
            current.copy(bands = updated, currentPreset = EqPreset.CUSTOM, customBandsMb = updated.map { it.levelMb })
        }
        applyEqCurve()
    }

    fun setPreampGain(gainDb: Float) {
        _audioLabState.update { it.copy(preampGainDb = gainDb.coerceIn(-12f, 12f)) }
        pushEarSettings()
    }

    fun applyPreset(preset: EqPreset) {
        _audioLabState.update { current ->
            val levels = if (preset == EqPreset.CUSTOM) current.customBandsMb
            else preset.gainsDb.map { (it * 100).toInt() }
            current.copy(
                currentPreset = preset,
                bands = current.bands.mapIndexed { i, band -> band.copy(levelMb = levels.getOrElse(i) { 0 }) }
            )
        }
        applyEqCurve()
    }

    /** Stores the current curve as the Custom preset. */
    fun saveCustomPreset() {
        _audioLabState.update { it.copy(customBandsMb = it.bands.map { b -> b.levelMb }, currentPreset = EqPreset.CUSTOM) }
    }

    fun resetEqualizer() = applyPreset(EqPreset.FLAT)

    fun autoApplyPresetForGenre(genre: String) {
        if (!_audioLabState.value.autoPresetByGenre) return
        val preset = when (genre.lowercase()) {
            "rock" -> EqPreset.ROCK
            "metal" -> EqPreset.METAL
            "pop" -> EqPreset.POP
            "jazz", "blues" -> EqPreset.JAZZ
            "classical", "symphony" -> EqPreset.CLASSICAL
            "hip-hop", "rap" -> EqPreset.HIPHOP
            "edm", "electronic", "dance" -> EqPreset.EDM
            "acoustic", "folk" -> EqPreset.ACOUSTIC
            "podcast", "vocal" -> EqPreset.VOCAL
            else -> return
        }
        applyPreset(preset)
    }

    fun setAutoPresetByGenre(enabled: Boolean) {
        _audioLabState.update { it.copy(autoPresetByGenre = enabled) }
    }

    /**
     * The platform Equalizer usually exposes 5 bands (60Hz…14kHz) rather than our 10. Each native
     * band is set to our curve interpolated (on a log-frequency axis) at its center frequency.
     */
    private fun applyEqCurve() {
        val eq = equalizer ?: return
        val bands = _audioLabState.value.bands
        runCatching {
            val range = eq.bandLevelRange
            val count = eq.numberOfBands.toInt()
            for (b in 0 until count) {
                val centerHz = eq.getCenterFreq(b.toShort()) / 1000.0
                val level = interpolateCurve(bands, centerHz)
                eq.setBandLevel(b.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
            }
        }
    }

    private fun interpolateCurve(bands: List<EqBand>, hz: Double): Int {
        if (bands.isEmpty()) return 0
        if (hz <= bands.first().centerFreqHz) return bands.first().levelMb
        if (hz >= bands.last().centerFreqHz) return bands.last().levelMb
        for (i in 0 until bands.lastIndex) {
            val a = bands[i]
            val b = bands[i + 1]
            if (hz >= a.centerFreqHz && hz <= b.centerFreqHz) {
                val t = (ln(hz) - ln(a.centerFreqHz.toDouble())) / (ln(b.centerFreqHz.toDouble()) - ln(a.centerFreqHz.toDouble()))
                return (a.levelMb + (b.levelMb - a.levelMb) * t).toInt()
            }
        }
        return 0
    }

    // -----------------------------------------------------------------------------------------
    // Spatial & enhancements
    // -----------------------------------------------------------------------------------------

    fun setSpatialAudioEnabled(enabled: Boolean) {
        _audioLabState.update { it.copy(spatialAudioEnabled = enabled) }
    }

    fun setVirtualSurroundEnabled(enabled: Boolean) {
        _audioLabState.update { it.copy(virtualSurroundEnabled = enabled) }
        runCatching { virtualizer?.enabled = enabled }
    }

    fun setSoundStage3D(percentage: Int) {
        _audioLabState.update { it.copy(soundStage3D = percentage.coerceIn(0, 100)) }
        runCatching { virtualizer?.setStrength((percentage * 10).coerceIn(0, 1000).toShort()) }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        _audioLabState.update { it.copy(bassBoostEnabled = enabled) }
        runCatching { bassBoost?.enabled = enabled }
    }

    fun setBassBoostStrength(strength: Int) {
        _audioLabState.update { it.copy(bassBoostStrength = strength.coerceIn(0, 1000)) }
        runCatching { bassBoost?.setStrength(strength.coerceIn(0, 1000).toShort()) }
    }

    fun setLoudnessEnabled(enabled: Boolean) {
        _audioLabState.update { it.copy(loudnessEnabled = enabled) }
        runCatching { loudnessEnhancer?.enabled = enabled }
    }

    fun setLoudnessGain(gainMb: Int) {
        _audioLabState.update { it.copy(loudnessGainMb = gainMb.coerceIn(0, 1000)) }
        runCatching { loudnessEnhancer?.setTargetGain(gainMb.coerceIn(0, 1000)) }
    }

    // -----------------------------------------------------------------------------------------
    // Ear controls (applied in real time by EarControlAudioProcessor)
    // -----------------------------------------------------------------------------------------

    fun setEarBalance(balance: Float) {
        _audioLabState.update { it.copy(balance = balance.coerceIn(-1f, 1f)) }
        pushEarSettings()
    }

    fun setLeftEarVolume(percent: Int) {
        _audioLabState.update { it.copy(leftVolumePercent = percent.coerceIn(0, 100)) }
        pushEarSettings()
    }

    fun setRightEarVolume(percent: Int) {
        _audioLabState.update { it.copy(rightVolumePercent = percent.coerceIn(0, 100)) }
        pushEarSettings()
    }

    fun setChannelSwap(enabled: Boolean) {
        _audioLabState.update { it.copy(isChannelSwapEnabled = enabled) }
        pushEarSettings()
    }

    fun setMonoMode(enabled: Boolean) {
        _audioLabState.update { it.copy(isMonoModeEnabled = enabled) }
        pushEarSettings()
    }

    fun resetEarControls() {
        _audioLabState.update {
            it.copy(leftVolumePercent = 100, rightVolumePercent = 100, balance = 0f, isChannelSwapEnabled = false, isMonoModeEnabled = false)
        }
        pushEarSettings()
    }

    fun setHearingCompensation(enabled: Boolean, leftCurve: HearingEarCurve? = null, rightCurve: HearingEarCurve? = null) {
        _audioLabState.update { current ->
            current.copy(
                isHearingCompensationEnabled = enabled,
                leftHearingCurve = leftCurve ?: current.leftHearingCurve,
                rightHearingCurve = rightCurve ?: current.rightHearingCurve
            )
        }
    }

    private fun pushEarSettings() {
        val s = _audioLabState.value
        earProcessor.update(
            leftPercent = s.leftVolumePercent,
            rightPercent = s.rightVolumePercent,
            balance = s.balance,
            swapChannels = s.isChannelSwapEnabled,
            monoMix = s.isMonoModeEnabled,
            preampDb = if (s.equalizerEnabled) s.preampGainDb else 0f
        )
    }

    private fun applyAllSettings() {
        val s = _audioLabState.value
        runCatching {
            equalizer?.enabled = s.equalizerEnabled
            applyEqCurve()
            bassBoost?.setStrength(s.bassBoostStrength.toShort())
            bassBoost?.enabled = s.bassBoostEnabled
            virtualizer?.setStrength((s.soundStage3D * 10).coerceIn(0, 1000).toShort())
            virtualizer?.enabled = s.virtualSurroundEnabled
            loudnessEnhancer?.setTargetGain(s.loudnessGainMb)
            loudnessEnhancer?.enabled = s.loudnessEnabled
        }
        pushEarSettings()
    }

    // -----------------------------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------------------------

    private suspend fun restoreSavedSettings() {
        val saved = runCatching { preferencesManager.loadAudioSettings() }.getOrNull() ?: return
        val preset = EqPreset.fromName(saved.preset) ?: EqPreset.FLAT
        val custom = saved.customBandsMb?.takeIf { it.size == 10 } ?: List(10) { 0 }
        val levels = saved.bandsMb?.takeIf { it.size == 10 } ?: preset.gainsDb.map { (it * 100).toInt() }
        _audioLabState.update { s ->
            s.copy(
                equalizerEnabled = saved.eqEnabled,
                currentPreset = preset,
                bands = s.bands.mapIndexed { i, b -> b.copy(levelMb = levels[i]) },
                customBandsMb = custom,
                preampGainDb = saved.preampDb,
                bassBoostStrength = saved.bassBoost,
                bassBoostEnabled = saved.bassBoostEnabled,
                soundStage3D = saved.virtualizer,
                virtualSurroundEnabled = saved.virtualizerEnabled,
                loudnessGainMb = saved.loudnessGain,
                loudnessEnabled = saved.loudnessEnabled,
                spatialAudioEnabled = saved.spatial,
                isMonoModeEnabled = saved.mono,
                isChannelSwapEnabled = saved.swap,
                balance = saved.balance,
                leftVolumePercent = saved.leftVolume,
                rightVolumePercent = saved.rightVolume,
                autoPresetByGenre = saved.autoPresetByGenre,
                deviceProfiles = saved.deviceProfiles.mapNotNull { (k, v) ->
                    val type = AudioDeviceType.entries.firstOrNull { it.name == k }
                    val p = EqPreset.fromName(v)
                    if (type != null && p != null) type to p else null
                }.toMap()
            )
        }
        applyAllSettings()
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeAndPersist() {
        _audioLabState
            .map { s ->
                SavedAudioSettings(
                    eqEnabled = s.equalizerEnabled,
                    preset = s.currentPreset.name,
                    bandsMb = s.bands.map { it.levelMb },
                    customBandsMb = s.customBandsMb,
                    preampDb = s.preampGainDb,
                    bassBoost = s.bassBoostStrength,
                    bassBoostEnabled = s.bassBoostEnabled,
                    virtualizer = s.soundStage3D,
                    virtualizerEnabled = s.virtualSurroundEnabled,
                    loudnessGain = s.loudnessGainMb,
                    loudnessEnabled = s.loudnessEnabled,
                    spatial = s.spatialAudioEnabled,
                    mono = s.isMonoModeEnabled,
                    swap = s.isChannelSwapEnabled,
                    balance = s.balance,
                    leftVolume = s.leftVolumePercent,
                    rightVolume = s.rightVolumePercent,
                    autoPresetByGenre = s.autoPresetByGenre,
                    deviceProfiles = s.deviceProfiles.map { (k, v) -> k.name to v.name }.toMap()
                )
            }
            .distinctUntilChanged()
            .debounce(500)
            .collect { runCatching { preferencesManager.saveAudioSettings(it) } }
    }

    // -----------------------------------------------------------------------------------------
    // Spectrum visualizer: real FFT of the playing audio (tapped in the ExoPlayer pipeline), with
    // the current EQ curve applied because platform EQ effects run after that tap.
    // -----------------------------------------------------------------------------------------

    private fun startSpectrumVisualizer() {
        scope.launch(Dispatchers.Default) {
            val analyzer = earProcessor.analyzer
            var smoothed = List(32) { 0.04f }
            while (isActive) {
                val state = _audioLabState.value
                val measured = if (isPlaying) analyzer.bands() else null
                val target = List(32) { i ->
                    val level = measured?.get(i) ?: 0.04f
                    if (measured == null) return@List level
                    val bandPos = i / 31f * 9f
                    val lo = bandPos.toInt().coerceIn(0, 9)
                    val hi = (lo + 1).coerceAtMost(9)
                    val eqDb = if (state.equalizerEnabled) {
                        val t = bandPos - lo
                        state.bands[lo].levelDb * (1 - t) + state.bands[hi].levelDb * t
                    } else 0f
                    (level + (eqDb + state.preampGainDb) / 72f).coerceIn(0.04f, 1f)
                }
                // Fast attack, slower release, like a real analyser.
                smoothed = smoothed.zip(target) { s, t -> if (t > s) s + (t - s) * 0.7f else s + (t - s) * 0.25f }
                _spectrum.value = smoothed
                delay(if (isPlaying) 50 else 250)
            }
        }
    }

    fun releaseAudioEffects() {
        runCatching {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            loudnessEnhancer?.release()
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
    }
}
