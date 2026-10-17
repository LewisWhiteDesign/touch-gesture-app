package com.lewisw.lockgestures.service

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.lewisw.lockgestures.model.SourceKind
import kotlin.math.abs

/**
 * Turns raw motion into "flicks" in one of six directions.
 *
 * - Up / Down: tipping the top edge away / towards you (gyroscope, X axis)
 * - Left / Right: twisting like a steering wheel (gyroscope, Z axis)
 * - Back / Forth: pulling towards / pushing away from you (linear acceleration, Z axis)
 *
 * A flick of the wrist always has a "return" movement in the opposite direction,
 * so after a flick the opposite direction on the same axis is ignored briefly.
 */
class MotionDetector(
    private val sensorManager: SensorManager,
    private val sensitivity: () -> Int,
    private val onFlick: (SourceKind) -> Unit,
) : SensorEventListener {

    private val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private var running = false

    /** Per-axis: time of the last flick and its direction (+1 / -1). */
    private val lastAt = LongArray(3)
    private val lastSign = IntArray(3)

    fun start() {
        if (running) return
        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        linear?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        running = true
    }

    fun stop() {
        if (!running) return
        sensorManager.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val s = sensitivity()
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val t = when (s) { 0 -> 7f; 2 -> 3.5f; else -> 5f } // rad/s
                val x = event.values[0]
                val z = event.values[2]
                if (abs(x) >= abs(z)) {
                    if (abs(x) > t) check(AXIS_PITCH, if (x < 0) -1 else 1,
                        if (x < 0) SourceKind.GYRO_UP else SourceKind.GYRO_DOWN)
                } else {
                    if (abs(z) > t) check(AXIS_TWIST, if (z > 0) 1 else -1,
                        if (z > 0) SourceKind.GYRO_LEFT else SourceKind.GYRO_RIGHT)
                }
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val t = when (s) { 0 -> 12f; 2 -> 5f; else -> 8f } // m/s²
                val z = event.values[2]
                if (abs(z) > t && abs(z) > abs(event.values[0]) && abs(z) > abs(event.values[1])) {
                    check(AXIS_PUSH, if (z > 0) 1 else -1,
                        if (z > 0) SourceKind.GYRO_BACK else SourceKind.GYRO_FORTH)
                }
            }
        }
    }

    private fun check(axis: Int, sign: Int, kind: SourceKind) {
        val now = System.currentTimeMillis()
        val since = now - lastAt[axis]
        if (sign == lastSign[axis] && since < SAME_DIRECTION_GAP_MS) return
        if (sign != lastSign[axis] && since < RETURN_IGNORE_MS) return
        lastAt[axis] = now
        lastSign[axis] = sign
        onFlick(kind)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private companion object {
        const val AXIS_PITCH = 0
        const val AXIS_TWIST = 1
        const val AXIS_PUSH = 2
        const val SAME_DIRECTION_GAP_MS = 250L
        const val RETURN_IGNORE_MS = 450L
    }
}
