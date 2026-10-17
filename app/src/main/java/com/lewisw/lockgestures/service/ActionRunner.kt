package com.lewisw.lockgestures.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Path
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
class ActionRunner(private val service: AccessibilityService) {

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

    private fun tapTile(action: Action, report: (String) -> Unit) {
        if (busy) return
        busy = true
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        val started = System.currentTimeMillis()

        fun attempt() {
            val node = findNode(action.tileLabels)
            when {
                node != null -> {
                    val ok = clickNode(node)
                    handler.postDelayed({ closeShade(); busy = false }, 450)
                    report(if (ok) "tapped ${action.short} tile" else "couldn't tap ${action.short} tile")
                }
                System.currentTimeMillis() - started > 1600 -> {
                    closeShade(); busy = false
                    report("couldn't find the ${action.short} tile — put it on the first page of Quick Settings")
                }
                else -> handler.postDelayed({ attempt() }, 150)
            }
        }
        handler.postDelayed({ attempt() }, 350)
    }

    private fun closeShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }

    /** Searches every window on screen for a node whose text or description starts with one of [labels]. */
    private fun findNode(labels: List<String>): AccessibilityNodeInfo? {
        val wanted = labels.map { it.lowercase() }
        val roots = service.windows.mapNotNull { it.root } + listOfNotNull(service.rootInActiveWindow)
        for (root in roots) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            var visited = 0
            while (queue.isNotEmpty() && visited < 2000) {
                val n = queue.removeFirst()
                visited++
                val text = (n.contentDescription ?: n.text)?.toString()?.lowercase()?.trim()
                if (text != null && wanted.any { text == it || text.startsWith("$it,") || text.startsWith("$it ") || text.startsWith(it) }) {
                    return n
                }
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    /** Clicks the node, or its nearest clickable parent. */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            n = n.parent
        }
        return false
    }

    private companion object {
        val STEPS = listOf(3, 15, 35, 65, 100, 145, 195, 255)
    }
}
