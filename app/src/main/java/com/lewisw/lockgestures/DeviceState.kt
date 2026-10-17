package com.lewisw.lockgestures

import android.app.KeyguardManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.util.DisplayMetrics
import android.view.Display
import kotlin.math.max
import kotlin.math.min

/** Answers "is the phone folded?" and "is the lock screen showing?". */
class DeviceState(private val context: Context) {

    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val displays = context.getSystemService(DisplayManager::class.java)

    val isScreenOn: Boolean get() = power.isInteractive

    val isLocked: Boolean get() = keyguard.isKeyguardLocked

    /**
     * True when the cover screen is in use.
     *
     * The Fold's cover screen is tall and narrow (about 21:9), while the inner
     * screen is close to square. Checking the shape of the active display is a
     * reliable way to tell them apart without any special permissions.
     */
    val isFolded: Boolean
        get() {
            val display = displays.getDisplay(Display.DEFAULT_DISPLAY) ?: return true
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display.getRealMetrics(metrics)
            val long = max(metrics.widthPixels, metrics.heightPixels).toFloat()
            val short = min(metrics.widthPixels, metrics.heightPixels).toFloat()
            if (short <= 0f) return true
            return long / short >= FOLDED_ASPECT_RATIO
        }

    companion object {
        /** Cover screen ≈ 2.3, inner screen ≈ 1.1. Anything above 1.6 counts as folded. */
        private const val FOLDED_ASPECT_RATIO = 1.6f
    }
}
