package com.lewisw.lockgestures

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import com.lewisw.lockgestures.model.Action
import com.lewisw.lockgestures.service.GestureAccessibilityService

/**
 * Invisible screen that asks for the PIN/fingerprint, then carries out an
 * action that can't be done from the lock screen (open app, close apps).
 */
class UnlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        val action = intent.getStringExtra(EXTRA_ACTION)
            ?.let { n -> Action.entries.firstOrNull { it.name == n } }
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        if (action == null) { finish(); return }

        val keyguard = getSystemService(KeyguardManager::class.java)
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() {
                finish()
                GestureAccessibilityService.instance?.runAfterUnlock(action, pkg)
            }

            override fun onDismissCancelled() = finish()
            override fun onDismissError() = finish()
        })
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_PACKAGE = "package"
    }
}
