package com.lewisw.lockgestures

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

/** Turns the torch on and off, and keeps track of its current state. */
class TorchController(context: Context) {

    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val cameraId: String? = findFlashCamera()
    private var torchOn = false

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) torchOn = enabled
        }
    }

    val hasFlash: Boolean get() = cameraId != null

    fun start() {
        cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        cameraManager.unregisterTorchCallback(callback)
    }

    /**
     * Toggles the torch. Returns the new state (true = on),
     * or null if the device has no flash or the camera is busy.
     */
    fun toggle(): Boolean? {
        val id = cameraId ?: return null
        return try {
            val target = !torchOn
            cameraManager.setTorchMode(id, target)
            torchOn = target
            target
        } catch (e: Exception) {
            null
        }
    }

    private fun findFlashCamera(): String? = try {
        val ids = cameraManager.cameraIdList
        // Prefer the rear camera's flash, fall back to any camera with a flash
        ids.firstOrNull { id ->
            val c = cameraManager.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: ids.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: Exception) {
        null
    }
}
