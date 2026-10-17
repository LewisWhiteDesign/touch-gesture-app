package com.lewisw.lockgestures

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Settings screen. All the real work happens in [GestureService]. */
@SuppressLint("UseSwitchCompatOrMaterialCode", "SetTextI18n")
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var state: DeviceState
    private lateinit var status: TextView
    private lateinit var lastEvent: TextView

    private val prefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "last_event") runOnUiThread { refresh() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        state = DeviceState(this)
        status = findViewById(R.id.status)
        lastEvent = findViewById(R.id.lastEvent)

        findViewById<Switch>(R.id.switchEnabled).apply {
            isChecked = prefs.serviceEnabled
            setOnCheckedChangeListener { _, on ->
                prefs.serviceEnabled = on
                if (on) GestureService.start(this@MainActivity) else GestureService.stop(this@MainActivity)
                refresh()
            }
        }
        findViewById<Switch>(R.id.switchFolded).apply {
            isChecked = prefs.onlyWhenFolded
            setOnCheckedChangeListener { _, on -> prefs.onlyWhenFolded = on }
        }
        findViewById<Switch>(R.id.switchLocked).apply {
            isChecked = prefs.onlyWhenLocked
            setOnCheckedChangeListener { _, on -> prefs.onlyWhenLocked = on }
        }

        findViewById<RadioGroup>(R.id.sensitivity).apply {
            check(
                when (prefs.shakeSensitivity) {
                    0 -> R.id.sensLow
                    2 -> R.id.sensHigh
                    else -> R.id.sensMed
                }
            )
            setOnCheckedChangeListener { _, id ->
                prefs.shakeSensitivity = when (id) {
                    R.id.sensLow -> 0
                    R.id.sensHigh -> 2
                    else -> 1
                }
            }
        }

        findViewById<Button>(R.id.btnTest).setOnClickListener {
            if (!prefs.serviceEnabled) {
                Toast.makeText(this, "Turn on 'Enable lock-screen gestures' first", Toast.LENGTH_SHORT).show()
            } else {
                GestureService.start(this, GestureService.ACTION_TEST)
            }
        }

        findViewById<Button>(R.id.btnNotifications).setOnClickListener { requestNotifications() }
        findViewById<Button>(R.id.btnBattery).setOnClickListener { requestBatteryExemption() }
        findViewById<Button>(R.id.btnAppInfo).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }

        // Make sure the service is running if it should be (e.g. after an update)
        if (prefs.serviceEnabled) GestureService.start(this)
    }

    override fun onResume() {
        super.onResume()
        prefs.registerListener(prefsListener)
        refresh()
    }

    override fun onPause() {
        prefs.unregisterListener(prefsListener)
        super.onPause()
    }

    private fun refresh() {
        val power = getSystemService(PowerManager::class.java)
        val batteryOk = power.isIgnoringBatteryOptimizations(packageName)
        val notifOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        status.text = buildString {
            append("Gestures: ").append(if (prefs.serviceEnabled) "ON" else "OFF").append('\n')
            append("Screen right now: ").append(if (state.isFolded) "cover (folded)" else "inner (unfolded)").append('\n')
            append("Notifications allowed: ").append(if (notifOk) "yes" else "no").append('\n')
            append("Background running allowed: ").append(if (batteryOk) "yes" else "no")
        }
        lastEvent.text = "Last gesture: ${prefs.lastEvent}"
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            Toast.makeText(this, "Notifications already allowed", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        val power = getSystemService(PowerManager::class.java)
        if (power.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "Already allowed", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }
}
