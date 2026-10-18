package com.lewisw.lockgestures.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.lewisw.lockgestures.UnlockActivity
import com.lewisw.lockgestures.model.Action
import com.lewisw.lockgestures.model.GestureFunction

/**
 * Carries out actions. Most are done directly; the radio toggles (Wi-Fi,
 * airplane mode, hotspot…) are done by tapping the matching Quick Settings tile,
 * because Android doesn't let ordinary apps switch those directly.
 */
class ActionRunner(
    private val service: AccessibilityService,
    private val inspector: ScreenInspector,
    /** Called with true before an automated tap sequence and false after, so zones can hide. */
    private val onAutomation: (Boolean) -> Unit,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val torch = TorchController(service).also { it.start() }
    private var busy = false

    fun release() = torch.stop()

    /** Runs [f]'s action and reports a short result through [report]. */
    fun run(f: GestureFunction, locked: Boolean, report: (String) -> Unit) {
        val action = f.action ?: return
        if (action.needsUnlock && locked) {
            // Show the PIN prompt; UnlockActivity calls back into the service once unlocked.
            val i = Intent(service, UnlockActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(UnlockActivity.EXTRA_ACTION, action.name)
                .putExtra(UnlockActivity.EXTRA_PACKAGE, f.appPackage)
            service.startActivity(i)
            report("waiting for unlock")
            return
        }
        when (action) {
            Action.TORCH -> report(
                when (torch.toggle()) {
                    true -> "torch on"
                    false -> "torch off"
                    null -> if (torch.hasFlash) "torch busy" else "no flash on this device"
                }
            )
            Action.BRIGHTNESS_UP -> report(changeBrightness(+1))
            Action.BRIGHTNESS_DOWN -> report(changeBrightness(-1))
            Action.SILENT -> report(toggleSilent())
            Action.VOLUME_UP -> report(changeVolume(AudioManager.ADJUST_RAISE))
            Action.VOLUME_DOWN -> report(changeVolume(AudioManager.ADJUST_LOWER))
            Action.OPEN_APP -> report(openApp(f.appPackage))
            Action.CLOSE_APP -> closeApps(all = false, report)
            Action.CLOSE_ALL -> closeApps(all = true, report)
            else -> tapTile(action, report)
        }
    }

    // ---- Direct actions ------------------------------------------------------

    private fun changeBrightness(direction: Int): String {
        if (!Settings.System.canWrite(service)) return "brightness needs 'Modify system settings' permission"
        val cr = service.contentResolver
        return try {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
            val next = if (direction > 0) STEPS.firstOrNull { it > current } ?: STEPS.last()
            else STEPS.lastOrNull { it < current } ?: STEPS.first()
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, next)
            "brightness ${next * 100 / 255}%"
        } catch (e: Exception) {
            "brightness failed"
        }
    }

    private fun changeVolume(direction: Int): String = try {
        service.getSystemService(AudioManager::class.java).adjustSuggestedStreamVolume(
            direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI
        )
        if (direction == AudioManager.ADJUST_RAISE) "volume up" else "volume down"
    } catch (e: Exception) {
        "volume change blocked (Do Not Disturb?)"
    }

    private fun toggleSilent(): String {
        val audio = service.getSystemService(AudioManager::class.java)
        val nm = service.getSystemService(NotificationManager::class.java)
        return try {
            if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                if (nm.isNotificationPolicyAccessGranted) {
                    audio.ringerMode = AudioManager.RINGER_MODE_SILENT
                    "silent on"
                } else {
                    audio.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    "vibrate on (allow Do Not Disturb access for full silent)"
                }
            } else {
                audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
                "sound on"
            }
        } catch (e: Exception) {
            "silent mode needs Do Not Disturb access"
        }
    }

    fun openApp(pkg: String?): String {
        if (pkg == null) return "no app chosen"
        val launch = service.packageManager.getLaunchIntentForPackage(pkg) ?: return "app not found"
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(launch)
        return "opened app"
    }

    /** Opens Recents and either swipes away the top app or taps "Close all". */
    fun closeApps(all: Boolean, report: (String) -> Unit) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            if (all) {
                val node = findNode(listOf("Close all", "Clear all"))
                if (node != null && clickNode(node)) report("closed all apps")
                else {
                    report("couldn't find 'Close all' — no apps open?")
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }
            } else {
                val m = service.resources.displayMetrics
                val path = Path().apply {
                    moveTo(m.widthPixels / 2f, m.heightPixels * 0.55f)
                    lineTo(m.widthPixels / 2f, m.heightPixels * 0.05f)
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, 200))
                    .build()
                service.dispatchGesture(gesture, null, null)
                handler.postDelayed({ service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }, 450)
                report("closed app")
            }
        }, 700)
    }

    // ---- Quick Settings tile tapping ----------------------------------------

    /**
     * Opens the Quick Settings panel, taps the tile's icon exactly like a finger
     * would, then closes the panel again. Our own zones are hidden meanwhile so
     * they can't get in the way of the tap.
     */
    private fun tapTile(action: Action, report: (String) -> Unit) {
        if (busy) return
        busy = true
        onAutomation(true)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        val started = System.currentTimeMillis()

        fun finish(message: String) {
            report(message)
            closeShade()
            handler.postDelayed({ busy = false; onAutomation(false) }, 300)
        }

        fun checkResult() {
            if (inspector.bouncerShowing()) {
                // Samsung wants the PIN for this tile: cancel the prompt rather than leave it up.
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                finish("Samsung asked for your PIN for ${action.short} — turn off 'Lock network and security'")
            } else {
                finish("tapped ${action.short}")
            }
        }

        fun attempt() {
            val node = inspector.findNode(action.tileLabels)
            when {
                node != null -> {
                    val tile = clickableAncestor(node) ?: node
                    val target = tileIcon(tile) ?: tile
                    val r = Rect()
                    target.getBoundsInScreen(r)
                    if (r.isEmpty) {
                        clickNode(node)
                        handler.postDelayed({ checkResult() }, 350)
                    } else {
                        tapAt(r.exactCenterX(), r.exactCenterY()) { ok ->
                            if (!ok) clickNode(node)
                            handler.postDelayed({ checkResult() }, 350)
                        }
                    }
                }
                System.currentTimeMillis() - started > 1500 ->
                    finish("couldn't find the ${action.short} tile — put it on the first page of Quick Settings")
                else -> handler.postDelayed({ attempt() }, 100)
            }
        }
        handler.postDelayed({ attempt() }, 250)
    }

    /** Sends a real tap to the screen at (x, y). */
    private fun tapAt(x: Float, y: Float, done: (Boolean) -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        val sent = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = done(true)
            override fun onCancelled(gestureDescription: GestureDescription?) = done(false)
        }, null)
        if (!sent) done(false)
    }

    private fun closeShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n
            n = n.parent
        }
        return null
    }

    /** The tile's icon: tapping the icon toggles the tile, whereas the label can open its details. */
    private fun tileIcon(tile: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(tile)
        var seen = 0
        while (queue.isNotEmpty() && seen < 60) {
            val n = queue.removeFirst()
            seen++
            if (n !== tile && n.className?.toString()?.endsWith("ImageView") == true && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun findNode(labels: List<String>) = inspector.findNode(labels)

    /** Clicks the node, or its nearest clickable parent. */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean =
        clickableAncestor(node)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false

    private companion object {
        val STEPS = listOf(3, 15, 35, 65, 100, 145, 195, 255)
    }
}
