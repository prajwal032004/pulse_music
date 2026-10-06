package com.example.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/**
 * Fires [onShake] on a deliberate shake: two strong jolts within half a second, with a cooldown
 * so one shake never skips more than one track. Only listens while [start]ed.
 */
class ShakeDetector(context: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var listening = false
    private var firstJoltAt = 0L
    private var lastShakeAt = 0L

    val isSupported: Boolean get() = accelerometer != null

    fun start() {
        if (listening || accelerometer == null) return
        listening = sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!listening) return
        sensorManager.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (gForce < THRESHOLD_G) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastShakeAt < COOLDOWN_MS) return
        if (firstJoltAt == 0L || now - firstJoltAt > WINDOW_MS) {
            firstJoltAt = now
        } else if (now - firstJoltAt > 80) {
            firstJoltAt = 0L
            lastShakeAt = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val THRESHOLD_G = 2.4f
        const val WINDOW_MS = 500L
        const val COOLDOWN_MS = 1200L
    }
}
