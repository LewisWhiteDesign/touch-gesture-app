package com.lewisw.lockgestures.model

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for zones, functions and settings.
 * The UI and the background service both observe these flows.
 */
object Store {
    private lateinit var sp: SharedPreferences

    private val _config = MutableStateFlow(Config())
    val config: StateFlow<Config> = _config.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _lastEvent = MutableStateFlow("None yet")
    val lastEvent: StateFlow<String> = _lastEvent.asStateFlow()

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("lockgestures", Context.MODE_PRIVATE)
        _config.value = Config.fromJson(sp.getString("config", null))
        _settings.value = AppSettings(
            enabled = sp.getBoolean("enabled", true),
            onlyWhenFolded = sp.getBoolean("only_folded", true),
            onlyWhenLocked = sp.getBoolean("only_locked", true),
            showZoneOutlines = sp.getBoolean("show_outlines", true),
            motionSensitivity = sp.getInt("motion_sensitivity", 1),
            drawStrictness = sp.getInt("draw_strictness", 1),
        )
        _lastEvent.value = sp.getString("last_event", "None yet") ?: "None yet"
    }

    fun updateConfig(change: (Config) -> Config) {
        val next = change(_config.value)
        _config.value = next
        sp.edit().putString("config", next.toJson()).apply()
    }

    fun updateSettings(change: (AppSettings) -> AppSettings) {
        val s = change(_settings.value)
        _settings.value = s
        sp.edit()
            .putBoolean("enabled", s.enabled)
            .putBoolean("only_folded", s.onlyWhenFolded)
            .putBoolean("only_locked", s.onlyWhenLocked)
            .putBoolean("show_outlines", s.showZoneOutlines)
            .putInt("motion_sensitivity", s.motionSensitivity)
            .putInt("draw_strictness", s.drawStrictness)
            .apply()
    }

    fun setLastEvent(text: String) {
        _lastEvent.value = text
        sp.edit().putString("last_event", text).apply()
    }
}
