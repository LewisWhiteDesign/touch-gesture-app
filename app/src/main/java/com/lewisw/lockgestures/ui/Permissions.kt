package com.lewisw.lockgestures.ui

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.lewisw.lockgestures.service.GestureAccessibilityService

/** Checks and shortcuts for the permissions the app needs. */
object Permissions {

    fun accessibilityOn(context: Context): Boolean {
        val me = ComponentName(context, GestureAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    fun dndAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun writeSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun openAccessibility(context: Context) =
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openDnd(context: Context) =
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    fun openWriteSettings(context: Context) = context.startActivity(
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
    )

    fun openAppInfo(context: Context) = context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )
}
