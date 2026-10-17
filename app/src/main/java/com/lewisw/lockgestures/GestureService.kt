package com.lewisw.lockgestures

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Runs in the background and listens for gestures while the lock screen is
 * showing. The motion sensor is only switched on while the screen is on and
 * locked, so it costs almost no battery the rest of the time.
 */
class GestureService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var state: DeviceState
    private lateinit var torch: TorchController
    private lateinit var sensorManager: SensorManager
    private lateinit var shake: ShakeDetector
    private var accelerometer: Sensor? = null
    private var listening = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = updateListening()
    }

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != "last_event") updateListening()
        }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        state = DeviceState(this)
        torch = TorchController(this).also { it.start() }
        sensorManager = getSystemService(SensorManager::class.java)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        shake = ShakeDetector(
            thresholdProvider = { ShakeDetector.thresholdFor(prefs.shakeSensitivity) },
            onShake = ::onShake,
        )

        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        prefs.registerListener(prefsListener)
        updateListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TEST) runTorchAction("Test button")
        updateListening()
        return START_STICKY
    }

    override fun onDestroy() {
        stopListening()
        unregisterReceiver(screenReceiver)
        prefs.unregisterListener(prefsListener)
        torch.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---- Sensor on/off -----------------------------------------------------

    private fun updateListening() {
        val shouldListen = state.isScreenOn && (!prefs.onlyWhenLocked || state.isLocked)
        if (shouldListen) startListening() else stopListening()
    }

    private fun startListening() {
        if (listening) return
        val sensor = accelerometer ?: return
        sensorManager.registerListener(shake, sensor, SensorManager.SENSOR_DELAY_GAME)
        listening = true
    }

    private fun stopListening() {
        if (!listening) return
        sensorManager.unregisterListener(shake)
        listening = false
    }

    // ---- Gestures ----------------------------------------------------------

    private fun onShake() {
        if (prefs.onlyWhenFolded && !state.isFolded) return
        if (prefs.onlyWhenLocked && !state.isLocked) return
        runTorchAction("Shake")
    }

    private fun runTorchAction(source: String) {
        val result = torch.toggle()
        val outcome = when (result) {
            true -> "torch ON"
            false -> "torch OFF"
            null -> if (torch.hasFlash) "torch busy (camera in use?)" else "no flash on this device"
        }
        vibrate(result != false)
        val time = SimpleDateFormat("HH:mm:ss", Locale.UK).format(Date())
        prefs.lastEvent = "$source at $time → $outcome"
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    @Suppress("DEPRECATION")
    private fun vibrate(on: Boolean) {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        val effect = if (on) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_DOUBLE_CLICK
        vibrator.vibrate(VibrationEffect.createPredefined(effect))
    }

    // ---- Notification ------------------------------------------------------

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Gesture listener", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shown while Lock Gestures is active" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Lock Gestures is active")
            .setContentText("Last: ${prefs.lastEvent}")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "gesture_listener"
        private const val NOTIFICATION_ID = 1
        const val ACTION_TEST = "com.lewisw.lockgestures.TEST"

        fun start(context: Context, action: String? = null) {
            val intent = Intent(context, GestureService::class.java).setAction(action)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GestureService::class.java))
        }
    }
}
