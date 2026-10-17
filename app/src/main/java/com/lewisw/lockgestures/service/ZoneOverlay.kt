package com.lewisw.lockgestures.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.lewisw.lockgestures.model.Trigger
import com.lewisw.lockgestures.model.TouchZone
import kotlin.math.abs

/**
 * Places invisible (or faintly outlined) touch zones over the lock screen.
 * Only accessibility services may draw over the lock screen, which is why
 * this lives inside the accessibility service.
 */
class ZoneOverlay(private val context: Context, private val wm: WindowManager) {

    private val views = mutableListOf<View>()
    private var signature = ""

    /**
     * Shows the given zones. [hasDoubleTap] says whether a zone needs to wait to
     * tell a single tap from a double tap.
     */
    fun show(
        zones: List<Pair<Int, TouchZone>>,
        screenW: Int,
        screenH: Int,
        outlines: Boolean,
        hasDoubleTap: (Int) -> Boolean,
        onGesture: (zoneId: Int, Trigger) -> Unit,
    ) {
        val sig = buildString {
            append("$screenW x $screenH $outlines;")
            zones.forEach { (n, z) -> append("$n:${z.id}:${z.left},${z.top},${z.right},${z.bottom};") }
            zones.forEach { append(hasDoubleTap(it.second.id)) }
        }
        if (sig == signature) return
        hide()
        signature = sig

        zones.forEach { (number, zone) ->
            val view = ZoneView(context, number, outlines, hasDoubleTap(zone.id)) { trigger ->
                onGesture(zone.id, trigger)
            }
            val lp = WindowManager.LayoutParams(
                (zone.width * screenW).toInt().coerceAtLeast(1),
                (zone.height * screenH).toInt().coerceAtLeast(1),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (zone.left * screenW).toInt()
                y = (zone.top * screenH).toInt()
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                title = "LockGestures zone $number"
            }
            runCatching { wm.addView(view, lp); views += view }
        }
    }

    fun hide() {
        views.forEach { runCatching { wm.removeView(it) } }
        views.clear()
        signature = ""
    }

    @SuppressLint("ViewConstructor")
    private class ZoneView(
        context: Context,
        private val number: Int,
        private val outlines: Boolean,
        private val waitForDoubleTap: Boolean,
        private val onGesture: (Trigger) -> Unit,
    ) : View(context) {

        private val density = resources.displayMetrics.density
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            color = Color.argb(90, 255, 255, 255)
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(8 * density, 6 * density), 0f)
        }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 255, 255, 255)
            textSize = 14 * density
            textAlign = Paint.Align.CENTER
        }
        private val rect = RectF()

        private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!waitForDoubleTap) onGesture(Trigger.SINGLE_TAP)
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (waitForDoubleTap) onGesture(Trigger.SINGLE_TAP)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (waitForDoubleTap) onGesture(Trigger.DOUBLE_TAP)
                return true
            }

            override fun onLongPress(e: MotionEvent) = onGesture(Trigger.PRESS_HOLD)

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val dx = if (e1 != null) e2.rawX - e1.rawX else velocityX
                val dy = if (e1 != null) e2.rawY - e1.rawY else velocityY
                val min = 30 * density
                if (abs(dx) < min && abs(dy) < min) return false
                onGesture(
                    if (abs(dx) > abs(dy)) {
                        if (dx > 0) Trigger.SWIPE_RIGHT else Trigger.SWIPE_LEFT
                    } else {
                        if (dy > 0) Trigger.SWIPE_DOWN else Trigger.SWIPE_UP
                    }
                )
                return true
            }
        })

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            detector.onTouchEvent(event)
            return true
        }

        override fun onDraw(canvas: Canvas) {
            if (!outlines) return
            val inset = stroke.strokeWidth
            rect.set(inset, inset, width - inset, height - inset)
            canvas.drawRoundRect(rect, 12 * density, 12 * density, stroke)
            canvas.drawText("$number", width / 2f, height / 2f + text.textSize / 3, text)
        }
    }
}
