package com.lewisw.lockgestures

import android.content.Context
import android.content.SharedPreferences

/** Simple wrapper around the app's saved settings. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Master switch: should the gesture service be running at all? */
    var serviceEnabled: Boolean
        get() = sp.getBoolean("service_enabled", false)
        set(v) = sp.edit().putBoolean("service_enabled", v).apply()

    /** Only react when the phone is folded (cover screen in use). */
    var onlyWhenFolded: Boolean
        get() = sp.getBoolean("only_folded", true)
        set(v) = sp.edit().putBoolean("only_folded", v).apply()

    /** Only react when the lock screen is showing. Turn off to test while unlocked. */
    var onlyWhenLocked: Boolean
        get() = sp.getBoolean("only_locked", true)
        set(v) = sp.edit().putBoolean("only_locked", v).apply()

    /** Shake sensitivity: 0 = low (hard shake), 1 = medium, 2 = high (gentle shake). */
    var shakeSensitivity: Int
        get() = sp.getInt("shake_sensitivity", 1)
        set(v) = sp.edit().putInt("shake_sensitivity", v).apply()

    /** Human-readable record of the most recent gesture, shown in the app. */
    var lastEvent: String
        get() = sp.getString("last_event", "None yet") ?: "None yet"
        set(v) = sp.edit().putString("last_event", v).apply()

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)
}
