package com.example

import android.content.Context
import com.example.audiolab.AudioDeviceMonitor
import com.example.audiolab.AudioLabManager
import com.example.audiolab.EarControlAudioProcessor
import com.example.data.MusicRepository
import com.example.data.PreferencesManager
import com.example.data.PulseDatabase
import com.example.playback.PlaybackManager
import com.example.smart.SmartFeatureEngine
import com.example.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val database = PulseDatabase.getInstance(context)
    val preferencesManager = PreferencesManager(context)
    val repository = MusicRepository(context, database.musicDao(), preferencesManager)
    val deviceMonitor = AudioDeviceMonitor(context)
    val earProcessor = EarControlAudioProcessor()
    val audioLabManager = AudioLabManager(context, preferencesManager, deviceMonitor, earProcessor)
    val playbackManager = PlaybackManager(context, audioLabManager, repository, preferencesManager, deviceMonitor, earProcessor)
    val smartEngine = SmartFeatureEngine(repository)
    val widgetUpdater = WidgetUpdater(context, this)
    val backupManager = com.example.data.BackupManager(context, database.musicDao(), repository)
    private val shakeDetector = com.example.playback.ShakeDetector(context) { playbackManager.seekToNext() }

    init {
        widgetUpdater.start()
        // Shake to skip: only listen to the accelerometer while music is playing and the option is on.
        scope.launch {
            kotlinx.coroutines.flow.combine(
                preferencesManager.settings.map { it.shakeToSkip },
                playbackManager.playbackState.map { it.isPlaying }
            ) { enabled, playing -> enabled && playing }
                .distinctUntilChanged()
                .collect { active -> if (active) shakeDetector.start() else shakeDetector.stop() }
        }
        // Re-scan when the minimum track length changes.
        scope.launch {
            preferencesManager.minTrackSeconds.distinctUntilChanged().drop(1).collect { repository.refreshLibrary() }
        }
        // Keep the current song's favorite flag (notification heart, widgets, UI) in sync with the library,
        // and restore the previous session once the library first loads.
        scope.launch {
            var restored = false
            repository.libraryData
                .map { data -> data.songs }
                .distinctUntilChanged()
                .collect { songs ->
                    if (!restored && songs.isNotEmpty()) {
                        restored = true
                        playbackManager.restoreLastSession(songs)
                    }
                    playbackManager.refreshCurrentSong()
                }
        }
    }
}
