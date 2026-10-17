package com.lewisw.lockgestures

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detects a deliberate shake: two or more sharp jolts in quick succession.
 *
 * A single bump (putting the phone down, a pocket knock) is ignored because it
 * only produces one jolt.
 */
class ShakeDetector(
    private val thresholdProvider: () -> Float,
    private val onShake: () -> Unit,
) : SensorEventListener {

    private var jolts = 0
    private var lastJoltAt = 0L
    private var lastShakeAt = 0L

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        // Acceleration beyond normal gravity, in m/s²
        val force = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()

        if (force < thresholdProvider()) return
        if (now - lastShakeAt < COOLDOWN_MS) return      // just fired, ignore
        if (now - lastJoltAt < MIN_GAP_MS) return        // same jolt, still ringing

        jolts = if (now - lastJoltAt > MAX_GAP_MS) 1 else jolts + 1
        lastJoltAt = now

        if (jolts >= JOLTS_NEEDED) {
            jolts = 0
            lastShakeAt = now
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val JOLTS_NEEDED = 2
        private const val MIN_GAP_MS = 120L
        private const val MAX_GAP_MS = 700L
        private const val COOLDOWN_MS = 1500L

        /** m/s² above gravity needed per jolt, for low / medium / high sensitivity. */
        fun thresholdFor(sensitivity: Int): Float = when (sensitivity) {
            0 -> 18f
            2 -> 9f
            else -> 13f
        }
    }
}
