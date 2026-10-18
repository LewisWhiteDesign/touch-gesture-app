package com.lewisw.lockgestures.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Reads what is on screen (through the accessibility service) to answer:
 * - is the PIN / pattern / password screen showing?
 * - where is a given Quick Settings tile?
 */
class ScreenInspector(private val service: AccessibilityService) {

    private fun roots(): List<AccessibilityNodeInfo> =
        runCatching { service.windows.mapNotNull { it.root } }.getOrDefault(emptyList())
            .filter { it.packageName?.toString() != service.packageName }

    /** Walks a window's node tree (breadth first, capped so it stays fast). */
    private inline fun walk(root: AccessibilityNodeInfo, limit: Int = 1500, visit: (AccessibilityNodeInfo) -> Boolean): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var seen = 0
        while (queue.isNotEmpty() && seen < limit) {
            val n = queue.removeFirst()
            seen++
            if (visit(n)) return true
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    /** True when the PIN, pattern or password entry screen is showing. */
    fun bouncerShowing(): Boolean {
        for (root in roots()) {
            if (root.packageName?.toString() != SYSTEM_UI) continue
            var digitKeys = 0
            val found = walk(root) { n ->
                if (!n.isVisibleToUser) return@walk false
                val id = n.viewIdResourceName?.lowercase().orEmpty()
                val cls = n.className?.toString().orEmpty()
                if (BOUNCER_IDS.any { id.contains(it) }) return@walk true
                if (cls.contains("LockPatternView") || cls.contains("NumPadKey")) return@walk true
                if (n.isPassword) return@walk true
                val label = (n.text ?: n.contentDescription)?.toString()?.trim().orEmpty()
                if (label.length == 1 && label[0].isDigit()) digitKeys++
                digitKeys >= 8 // a full number pad is on screen
            }
            if (found) return true
        }
        return false
    }

    /** Finds a node whose text or description matches one of [labels]. */
    fun findNode(labels: List<String>): AccessibilityNodeInfo? {
        val wanted = labels.map { it.lowercase() }
        for (root in roots()) {
            var match: AccessibilityNodeInfo? = null
            walk(root, limit = 2500) { n ->
                val text = (n.contentDescription ?: n.text)?.toString()?.lowercase()?.trim()
                if (text != null && n.isVisibleToUser && wanted.any { w ->
                        text == w || text.startsWith("$w,") || text.startsWith("$w ") || text.startsWith(w)
                    }
                ) {
                    match = n; true
                } else false
            }
            match?.let { return it }
        }
        return null
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
        val BOUNCER_IDS = listOf(
            "pinentry", "pin_view", "pinview", "keyguard_pin", "pin_container",
            "pattern", "password_entry", "passwordentry", "keyguard_password",
            "bouncer", "key_enter", "numpad", "digit_text",
        )
    }
}
