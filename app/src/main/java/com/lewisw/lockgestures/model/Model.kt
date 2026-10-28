package com.lewisw.lockgestures.model

import androidx.annotation.DrawableRes
import com.lewisw.lockgestures.R
import org.json.JSONArray
import org.json.JSONObject

/** What the user does. */
enum class Trigger(val label: String) {
    SINGLE_TAP("Single Tap"),
    DOUBLE_TAP("Double Tap"),
    SWIPE_UP("Swipe Up"),
    SWIPE_RIGHT("Swipe Right"),
    SWIPE_LEFT("Swipe Left"),
    SWIPE_DOWN("Swipe Down"),
    PRESS_HOLD("Press + Hold"),
    DRAW("Draw Shape");

    /** The label adapts to the input type so the dropdowns read naturally. */
    fun labelFor(kind: SourceKind?): String = when {
        kind?.isVolume == true -> when (this) {
            SINGLE_TAP -> "Single Press"
            DOUBLE_TAP -> "Double Press"
            else -> label
        }
        kind?.isGyro == true -> when (this) {
            SINGLE_TAP -> "Flick"
            DOUBLE_TAP -> "Double Flick"
            else -> label
        }
        else -> label
    }
}

/** Where the gesture happens: a drawn touch zone, a volume key or a motion direction. */
enum class SourceKind(val label: String, val help: String = "") {
    TOUCH("Zone"),
    VOLUME_UP("Volume Up"),
    VOLUME_DOWN("Volume Down"),
    GYRO_UP("Gyro Up", "Tip the top of the phone away from you"),
    GYRO_DOWN("Gyro Down", "Tip the top of the phone towards you"),
    GYRO_LEFT("Gyro Left", "Twist anticlockwise, like a steering wheel"),
    GYRO_RIGHT("Gyro Right", "Twist clockwise, like a steering wheel"),
    GYRO_BACK("Gyro Back", "Pull the phone sharply towards you"),
    GYRO_FORTH("Gyro Forth", "Push the phone sharply away from you");

    val isVolume get() = this == VOLUME_UP || this == VOLUME_DOWN
    val isGyro get() = name.startsWith("GYRO_")

    fun validTriggers(): List<Trigger> = when {
        this == TOUCH -> Trigger.entries
        isVolume -> listOf(Trigger.SINGLE_TAP, Trigger.DOUBLE_TAP, Trigger.PRESS_HOLD)
        // Drawing only makes sense on a touch zone
        else -> listOf(Trigger.SINGLE_TAP, Trigger.DOUBLE_TAP)
    }
}

/** A trigger source: either a touch zone (with its id) or a fixed hardware/motion source. */
data class Source(val kind: SourceKind, val zoneId: Int = 0) {
    fun encode(): String = if (kind == SourceKind.TOUCH) "touch:$zoneId" else kind.name

    companion object {
        fun decode(s: String): Source? = runCatching {
            if (s.startsWith("touch:")) Source(SourceKind.TOUCH, s.removePrefix("touch:").toInt())
            else Source(SourceKind.valueOf(s))
        }.getOrNull()
    }
}

enum class Action(
    val label: String,
    val short: String,
    @DrawableRes val icon: Int,
    val needsUnlock: Boolean = false,
    /** Name(s) of the Quick Settings tile this action taps, if it works that way. */
    val tileLabels: List<String> = emptyList(),
) {
    TORCH("Torch (On/Off)", "Torch", R.drawable.ic_act_torch),
    AIRPLANE("Airplane Mode (On/Off)", "Airplane Mode", R.drawable.ic_act_airplane,
        tileLabels = listOf("Airplane mode", "Flight mode", "Aeroplane mode")),
    WIFI("Wi-Fi (On/Off)", "Wi-Fi", R.drawable.ic_act_wifi,
        tileLabels = listOf("Wi-Fi", "Wi\u2011Fi", "WiFi", "Wifi", "WLAN")),
    BLUETOOTH("Bluetooth (On/Off)", "Bluetooth", R.drawable.ic_act_bluetooth, tileLabels = listOf("Bluetooth")),
    HOTSPOT("Wireless Hotspot (On/Off)", "Hotspot", R.drawable.ic_act_hotspot,
        tileLabels = listOf("Mobile Hotspot", "Hotspot", "Mobile hotspot")),
    NFC("NFC (On/Off)", "NFC", R.drawable.ic_act_nfc, tileLabels = listOf("NFC")),
    OPEN_APP("Open App (Unlock Required)", "Open App", R.drawable.ic_act_open_app, needsUnlock = true),
    CLOSE_APP("Close App (Unlock Required)", "Close App", R.drawable.ic_act_close_app, needsUnlock = true),
    CLOSE_ALL("Close All Apps (Unlock Required)", "Close All Apps", R.drawable.ic_act_close_all, needsUnlock = true),
    BRIGHTNESS_UP("Brightness Up", "Brightness Up", R.drawable.ic_act_brightness_up),
    BRIGHTNESS_DOWN("Brightness Down", "Brightness Down", R.drawable.ic_act_brightness_down),
    VOLUME_UP("Volume Up", "Volume Up", R.drawable.ic_act_volume_up),
    VOLUME_DOWN("Volume Down", "Volume Down", R.drawable.ic_act_volume_down),
    SILENT("Silent Mode (On/Off)", "Silent Mode", R.drawable.ic_act_silent),
    BATTERY_SAVER("Battery Saver (On/Off)", "Battery Saver", R.drawable.ic_act_battery_saver,
        tileLabels = listOf("Power saving", "Battery saver", "Power saving mode")),
}

/** A touch zone, stored as fractions (0..1) of the full screen so it survives size changes. */
data class TouchZone(
    val id: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width get() = right - left
    val height get() = bottom - top
}

/** One row in the Functions list. Fields stay null until the user picks them. */
data class GestureFunction(
    val id: Long,
    val trigger: Trigger? = null,
    val source: Source? = null,
    val action: Action? = null,
    val appPackage: String? = null,
    val appLabel: String? = null,
    /** For [Trigger.DRAW]: the user's recorded examples of the shape. */
    val shapes: List<Shape> = emptyList(),
) {
    val isComplete: Boolean
        get() = trigger != null && source != null && action != null &&
            (action != Action.OPEN_APP || appPackage != null) &&
            (trigger != Trigger.DRAW || shapes.isNotEmpty())
}

data class Config(
    val zones: List<TouchZone> = emptyList(),
    val functions: List<GestureFunction> = emptyList(),
    /** Height ÷ width of the cover screen, captured when zones were last set. */
    val screenAspect: Float = 2520f / 1080f,
    /** Fraction of the screen height used by the status bar and the navigation bar. */
    val topInset: Float = 0.04f,
    val bottomInset: Float = 0.05f,
) {
    fun zoneNumber(id: Int): Int = zones.indexOfFirst { it.id == id } + 1

    fun sourceLabel(s: Source): String =
        if (s.kind == SourceKind.TOUCH) "Zone ${zoneNumber(s.zoneId)}" else s.kind.label

    /** Every source the user could pick right now. */
    fun allSources(): List<Source> =
        zones.map { Source(SourceKind.TOUCH, it.id) } +
            SourceKind.entries.filter { it != SourceKind.TOUCH }.map { Source(it) }

    fun toJson(): String = JSONObject().apply {
        put("aspect", screenAspect.toDouble())
        put("topInset", topInset.toDouble())
        put("bottomInset", bottomInset.toDouble())
        put("zones", JSONArray().apply {
            zones.forEach { z ->
                put(JSONObject().apply {
                    put("id", z.id); put("l", z.left.toDouble()); put("t", z.top.toDouble())
                    put("r", z.right.toDouble()); put("b", z.bottom.toDouble())
                })
            }
        })
        put("functions", JSONArray().apply {
            functions.forEach { f ->
                put(JSONObject().apply {
                    put("id", f.id)
                    f.trigger?.let { put("trigger", it.name) }
                    f.source?.let { put("source", it.encode()) }
                    f.action?.let { put("action", it.name) }
                    f.appPackage?.let { put("pkg", it) }
                    f.appLabel?.let { put("appLabel", it) }
                    if (f.shapes.isNotEmpty()) put("shapes", ShapeRecognizer.toJson(f.shapes))
                })
            }
        })
    }.toString()

    companion object {
        fun fromJson(json: String?): Config {
            if (json.isNullOrBlank()) return Config()
            return runCatching {
                val o = JSONObject(json)
                val zs = o.optJSONArray("zones") ?: JSONArray()
                val fs = o.optJSONArray("functions") ?: JSONArray()
                Config(
                    zones = (0 until zs.length()).map { i ->
                        val z = zs.getJSONObject(i)
                        TouchZone(
                            z.getInt("id"), z.getDouble("l").toFloat(), z.getDouble("t").toFloat(),
                            z.getDouble("r").toFloat(), z.getDouble("b").toFloat()
                        )
                    },
                    functions = (0 until fs.length()).map { i ->
                        val f = fs.getJSONObject(i)
                        GestureFunction(
                            id = f.getLong("id"),
                            trigger = f.optString("trigger").takeIf { it.isNotEmpty() }
                                ?.let { n -> Trigger.entries.firstOrNull { it.name == n } },
                            source = f.optString("source").takeIf { it.isNotEmpty() }?.let { Source.decode(it) },
                            action = f.optString("action").takeIf { it.isNotEmpty() }
                                ?.let { n -> Action.entries.firstOrNull { it.name == n } },
                            appPackage = f.optString("pkg").takeIf { it.isNotEmpty() },
                            appLabel = f.optString("appLabel").takeIf { it.isNotEmpty() },
                            shapes = ShapeRecognizer.fromJson(f.optJSONArray("shapes")),
                        )
                    },
                    screenAspect = o.optDouble("aspect", 2520.0 / 1080.0).toFloat(),
                    topInset = o.optDouble("topInset", 0.04).toFloat(),
                    bottomInset = o.optDouble("bottomInset", 0.05).toFloat(),
                )
            }.getOrDefault(Config())
        }
    }
}

data class AppSettings(
    val enabled: Boolean = true,
    val onlyWhenFolded: Boolean = true,
    val onlyWhenLocked: Boolean = true,
    val showZoneOutlines: Boolean = true,
    /** 0 = low, 1 = medium, 2 = high. */
    val motionSensitivity: Int = 1,
    /** How closely a drawing must match: 0 = loose, 1 = normal, 2 = strict. */
    val drawStrictness: Int = 1,
)
