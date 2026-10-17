package com.lewisw.lockgestures.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.view.Display
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.lewisw.lockgestures.model.Action
import com.lewisw.lockgestures.model.Config
import com.lewisw.lockgestures.model.GestureFunction
import com.lewisw.lockgestures.model.Source
import com.lewisw.lockgestures.model.SourceKind
import com.lewisw.lockgestures.model.Store
import com.lewisw.lockgestures.model.Trigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The engine. Runs whenever the user has turned it on in Accessibility settings.
 * While the phone is folded and locked it shows the touch zones, listens to the
 * volume keys and watches for motion flicks, then runs the matching function.
 */
class GestureAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var state: DeviceState
    private lateinit var overlay: ZoneOverlay
    private lateinit var motion: MotionDetector
    private lateinit var runner: ActionRunner
    private var active = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Store.init(this)
        instance = this
        state = DeviceState(this)
        overlay = ZoneOverlay(this, getSystemService(WindowManager::class.java))
        motion = MotionDetector(
            getSystemService(SensorManager::class.java),
            sensitivity = { Store.settings.value.motionSensitivity },
            onFlick = { kind -> onPress(Source(kind)) },
        )
        runner = ActionRunner(this)

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        scope.launch {
            Store.config.combine(Store.settings) { c, s -> c to s }.collect { refresh() }
        }
        refresh()
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        if (::overlay.isInitialized) overlay.hide()
        if (::motion.isInitialized) motion.stop()
        if (::runner.isInitialized) runner.release()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Window changes include the lock screen appearing/disappearing.
        if (::state.isInitialized) refresh()
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Folding/unfolding changes the screen, so re-place the zones.
        if (::state.isInitialized) handler.postDelayed({ refresh() }, 300)
    }

    // ---- When to listen -----------------------------------------------------

    private fun shouldBeActive(): Boolean {
        val s = Store.settings.value
        return s.enabled && state.isScreenOn &&
            (!s.onlyWhenLocked || state.isLocked) &&
            (!s.onlyWhenFolded || state.isFolded)
    }

    private fun refresh() {
        val config = Store.config.value
        val settings = Store.settings.value
        active = shouldBeActive()
        val fns = config.functions.filter { it.isComplete }

        if (!active) {
            overlay.hide()
            motion.stop()
            return
        }

        // Touch zones: only show zones that have a function, so nothing else is blocked.
        val usedZoneIds = fns.mapNotNull { f -> f.source?.takeIf { it.kind == SourceKind.TOUCH }?.zoneId }.toSet()
        val zones = config.zones.mapIndexedNotNull { i, z -> if (z.id in usedZoneIds) (i + 1) to z else null }
        val (w, h) = screenSize()
        overlay.show(
            zones, w, h, settings.showZoneOutlines,
            hasDoubleTap = { id -> fns.any { it.source == Source(SourceKind.TOUCH, id) && it.trigger == Trigger.DOUBLE_TAP } },
            onGesture = { id, trigger -> fire(Source(SourceKind.TOUCH, id), trigger) },
        )

        if (fns.any { it.source?.kind?.isGyro == true }) motion.start() else motion.stop()
    }

    private fun screenSize(): Pair<Int, Int> {
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        display?.getRealMetrics(m)
        return m.widthPixels to m.heightPixels
    }

    // ---- Volume keys --------------------------------------------------------

    private var holdFired = false
    private val holdRunnable = Runnable {
        holdFired = true
        heldKey?.let { fire(Source(it), Trigger.PRESS_HOLD) }
    }
    private var heldKey: SourceKind? = null

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!active) return false
        val kind = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> SourceKind.VOLUME_UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> SourceKind.VOLUME_DOWN
            else -> return false
        }
        val fns = functionsFor(Source(kind))
        if (fns.isEmpty()) return false // let the volume key work normally

        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                holdFired = false
                heldKey = kind
                if (fns.any { it.trigger == Trigger.PRESS_HOLD }) handler.postDelayed(holdRunnable, HOLD_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(holdRunnable)
                if (!holdFired) onPress(Source(kind))
            }
        }
        return true
    }

    // ---- Single vs double press (volume keys and flicks) -------------------

    private var pendingSource: Source? = null
    private val pendingSingle = Runnable {
        pendingSource?.let { fire(it, Trigger.SINGLE_TAP) }
        pendingSource = null
    }

    private fun onPress(source: Source) {
        if (!active) return
        val wantsDouble = functionsFor(source).any { it.trigger == Trigger.DOUBLE_TAP }
        when {
            pendingSource == source -> {
                handler.removeCallbacks(pendingSingle)
                pendingSource = null
                fire(source, Trigger.DOUBLE_TAP)
            }
            wantsDouble -> {
                handler.removeCallbacks(pendingSingle)
                pendingSource?.let { fire(it, Trigger.SINGLE_TAP) }
                pendingSource = source
                handler.postDelayed(pendingSingle, if (source.kind.isGyro) FLICK_DOUBLE_MS else PRESS_DOUBLE_MS)
            }
            else -> fire(source, Trigger.SINGLE_TAP)
        }
    }

    // ---- Running functions --------------------------------------------------

    private fun functionsFor(source: Source): List<GestureFunction> =
        Store.config.value.functions.filter { it.isComplete && it.source == source }

    private fun fire(source: Source, trigger: Trigger) {
        if (!active) return
        val matches = functionsFor(source).filter { it.trigger == trigger }
        if (matches.isEmpty()) return
        vibrate()
        val config = Store.config.value
        matches.forEach { f ->
            runner.run(f, state.isLocked) { result -> report(config, f, result) }
        }
    }

    private fun report(config: Config, f: GestureFunction, result: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.UK).format(Date())
        val what = "${f.trigger?.labelFor(f.source?.kind)} · ${f.source?.let { config.sourceLabel(it) }}"
        Store.setLastEvent("$time  $what → $result")
    }

    /** Called by UnlockActivity once the user has entered their PIN. */
    fun runAfterUnlock(action: Action, pkg: String?) {
        handler.postDelayed({
            val f = GestureFunction(id = 0, action = action, appPackage = pkg)
            runner.run(f, locked = false) { result ->
                Store.setLastEvent(SimpleDateFormat("HH:mm:ss", Locale.UK).format(Date()) + "  ${action.short} → $result")
            }
        }, 400)
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val v = getSystemService(Vibrator::class.java) ?: return
        if (v.hasVibrator()) v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    companion object {
        private const val HOLD_MS = 600L
        private const val PRESS_DOUBLE_MS = 350L
        private const val FLICK_DOUBLE_MS = 900L

        /** The running service, if the user has it switched on. */
        @Volatile
        var instance: GestureAccessibilityService? = null
            private set
    }
}
