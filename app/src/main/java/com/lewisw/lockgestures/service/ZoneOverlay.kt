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
import kotlin.math.max
import kotlin.math.min

/**
 * Places invisible (or faintly outlined) touch zones over the lock screen.
 * Only accessibility services may draw over the lock screen, which is why
 * this lives inside the accessibility service.
 */
class ZoneOverlay(private val context: Context, private val wm: WindowManager) {

    private val views = mutableListOf<View>()
    private var signature = ""

    /** A zone to show, with the icons of the actions linked to it. */
    data class Spec(val zone: TouchZone, val icons: List<Int>, val waitForDoubleTap: Boolean)

    fun show(
        specs: List<Spec>,
        screenW: Int,
        screenH: Int,
        outlines: Boolean,
        onGesture: (zoneId: Int, Trigger) -> Unit,
    ) {
        val sig = "$screenW x $screenH $outlines;" + specs.joinToString(";")
        if (sig == signature) return
        hide()
        signature = sig

        specs.forEach { spec ->
            val zone = spec.zone
            val view = ZoneView(context, spec.icons, outlines, spec.waitForDoubleTap) { trigger ->
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
                title = "LockGestures zone ${zone.id}"
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
        iconIds: List<Int>,
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
        private val icons = iconIds.mapNotNull { id ->
            context.getDrawable(id)?.mutate()?.apply { setTint(Color.argb(150, 255, 255, 255)) }
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
            drawIcons(canvas)
        }

        /** Draws the linked actions' icons centred in the zone, wrapping onto extra rows if needed. */
        private fun drawIcons(canvas: Canvas) {
            if (icons.isEmpty()) return
            val gap = 6 * density
            var size = (22 * density).coerceAtMost(min(width, height) * 0.6f)
            val perRow = max(1, ((width - gap) / (size + gap)).toInt())
            val rows = (icons.size + perRow - 1) / perRow
            if (rows * (size + gap) > height) size = max(8 * density, height / rows - gap)
            val total = rows * size + (rows - 1) * gap
            var y = (height - total) / 2f
            icons.chunked(perRow).forEach { row ->
                val rowW = row.size * size + (row.size - 1) * gap
                var x = (width - rowW) / 2f
                row.forEach { d ->
                    d.setBounds(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt())
                    d.draw(canvas)
                    x += size + gap
                }
                y += size + gap
            }
        }
    }
}
