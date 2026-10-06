package com.example.audiolab

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.model.AudioDeviceType
import com.example.model.OutputDevice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Headphone/speaker connection changes, used for auto-pause/resume and device EQ profiles. */
sealed interface DeviceEvent {
    val device: OutputDevice

    data class Connected(override val device: OutputDevice) : DeviceEvent
    data class Disconnected(override val device: OutputDevice) : DeviceEvent
}

/**
 * Tracks wired, USB and Bluetooth (A2DP / LE Audio / hearing aid) outputs through
 * [AudioManager.registerAudioDeviceCallback]. No Bluetooth permission is needed because the
 * product names come from the audio framework, not the Bluetooth stack.
 */
class AudioDeviceMonitor(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val phoneSpeaker = OutputDevice(id = -1, name = "This phone", type = AudioDeviceType.SPEAKER)

    private val _connected = MutableStateFlow<List<OutputDevice>>(emptyList())
    val connectedOutputs: StateFlow<List<OutputDevice>> = _connected.asStateFlow()

    private val _current = MutableStateFlow(phoneSpeaker)
    /** The output music is routed to: the most recently connected personal device, else the speaker. */
    val currentOutput: StateFlow<OutputDevice> = _current.asStateFlow()

    private val _events = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DeviceEvent> = _events.asSharedFlow()

    private var initialScanDone = false

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val added = addedDevices.mapNotNull { it.toOutputDevice() }
            refresh()
            if (initialScanDone) {
                added.forEach { _events.tryEmit(DeviceEvent.Connected(it)) }
            }
            initialScanDone = true
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            val removed = removedDevices.mapNotNull { it.toOutputDevice() }
            refresh()
            removed.forEach { _events.tryEmit(DeviceEvent.Disconnected(it)) }
        }
    }

    init {
        refresh()
        // The callback fires once immediately with all current devices; that first batch is not
        // a "connection" event, so it is swallowed via initialScanDone.
        audioManager.registerAudioDeviceCallback(callback, handler)
    }

    private fun refresh() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .mapNotNull { it.toOutputDevice() }
            .distinctBy { it.type to it.name }
        _connected.value = devices
        _current.value = devices.maxByOrNull { priority(it.type) } ?: phoneSpeaker
    }

    private fun priority(type: AudioDeviceType): Int = when (type) {
        AudioDeviceType.USB -> 4
        AudioDeviceType.WIRED_HEADSET -> 3
        AudioDeviceType.BLUETOOTH -> 2
        AudioDeviceType.OTHER -> 1
        AudioDeviceType.SPEAKER -> 0
    }

    private fun AudioDeviceInfo.toOutputDevice(): OutputDevice? {
        if (!isSink) return null
        val (mapped, ble) = when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioDeviceType.BLUETOOTH to false
            AudioDeviceInfo.TYPE_HEARING_AID -> AudioDeviceType.BLUETOOTH to false
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG -> AudioDeviceType.WIRED_HEADSET to false
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioDeviceType.USB to false
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioDeviceType.SPEAKER to false
            else -> when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER) ->
                    AudioDeviceType.BLUETOOTH to true
                else -> return null
            }
        }
        if (mapped == AudioDeviceType.SPEAKER) return phoneSpeaker
        val rates = sampleRates
        val maxRate = if (rates.isNotEmpty()) rates.max() else 0
        val channels = channelCounts
        val maxChannels = if (channels.isNotEmpty()) channels.max() else 2
        val rawName = productName?.toString()?.trim().orEmpty()
        val name = when {
            rawName.isNotEmpty() && !rawName.equals(Build.MODEL, ignoreCase = true) -> rawName
            mapped == AudioDeviceType.WIRED_HEADSET -> "Wired headphones"
            mapped == AudioDeviceType.USB -> "USB audio"
            else -> "Bluetooth headphones"
        }
        return OutputDevice(
            id = id,
            name = name,
            type = mapped,
            maxSampleRateHz = maxRate,
            maxChannels = maxChannels,
            supportsHiRes = maxRate > 48000,
            isBle = ble
        )
    }

    fun release() {
        audioManager.unregisterAudioDeviceCallback(callback)
    }
}
