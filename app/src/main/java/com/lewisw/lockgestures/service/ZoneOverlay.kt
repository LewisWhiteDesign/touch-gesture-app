package com.lewisw.lockgestures.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.lewisw.lockgestures.model.Shape
import com.lewisw.lockgestures.model.ShapeRecognizer
import com.lewisw.lockgestures.model.TouchZone
import com.lewisw.lockgestures.model.Trigger
import kotlin.math.abs
import kotlin.math.hypot
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

    /**
     * A zone to show.
     * @param icons icons of the actions linked to it
     * @param waitForDoubleTap whether a single tap must wait to rule out a double tap
     * @param shapes recorded shapes for "Draw Shape" functions, keyed by function id
     */
    data class Spec(
        val zone: TouchZone,
        val icons: List<Int>,
        val waitForDoubleTap: Boolean,
        val shapes: Map<Long, List<Shape>>,
    ) {
        fun key() = "${zone}|$icons|$waitForDoubleTap|${shapes.hashCode()}"
    }

    fun show(
        specs: List<Spec>,
        screenW: Int,
        screenH: Int,
        outlines: Boolean,
        strictness: Int,
        onGesture: (zoneId: Int, trigger: Trigger, functionId: Long?) -> Unit,
    ) {
        val sig = "$screenW x $screenH $outlines $strictness;" + specs.joinToString(";") { it.key() }
        if (sig == signature) return
        hide()
        signature = sig

        specs.forEach { spec ->
            val zone = spec.zone
            val view = ZoneView(context, spec, outlines, strictness) { trigger, fnId ->
                onGesture(zone.id, trigger, fnId)
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

    /**
     * One zone. Handles taps, double taps, press-and-hold, swipes and drawn
     * shapes with a single touch tracker, so they don't fight each other.
     */
    @SuppressLint("ViewConstructor")
    private class ZoneView(
        context: Context,
        private val spec: Spec,
        private val outlines: Boolean,
        private val strictness: Int,
        private val onGesture: (Trigger, Long?) -> Unit,
    ) : View(context) {

        private val density = resources.displayMetrics.density
        private val slop = 12 * density
        private val handler = Handler(Looper.getMainLooper())
        private val hasDraw = spec.shapes.isNotEmpty()

        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            color = Color.argb(90, 255, 255, 255)
            pathEffect = DashPathEffect(floatArrayOf(8 * density, 6 * density), 0f)
        }
        private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4 * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.argb(200, 255, 255, 255)
        }
        private val icons = spec.icons.mapNotNull { id ->
            context.getDrawable(id)?.mutate()?.apply { setTint(Color.argb(150, 255, 255, 255)) }
        }
        private val rect = RectF()

        // Touch tracking
        private val strokes = mutableListOf<MutableList<Pair<Float, Float>>>()
        private val ink = Path()
        private var downX = 0f
        private var downY = 0f
        private var moved = false
        private var holdFired = false

        private val holdRunnable = Runnable {
            holdFired = true
            reset()
            onGesture(Trigger.PRESS_HOLD, null)
        }
        private val settleRunnable = Runnable { settle() }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handler.removeCallbacks(settleRunnable)
                    holdFired = false
                    moved = false
                    downX = e.x; downY = e.y
                    strokes += mutableListOf(e.x to e.y)
                    ink.moveTo(e.x, e.y)
                    handler.postDelayed(holdRunnable, HOLD_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (holdFired) return true
                    for (h in 0 until e.historySize) addPoint(e.getHistoricalX(h), e.getHistoricalY(h))
                    addPoint(e.x, e.y)
                    if (!moved && hypot(e.x - downX, e.y - downY) > slop) {
                        moved = true
                        handler.removeCallbacks(holdRunnable)
                    }
                    if (hasDraw) invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(holdRunnable)
                    if (holdFired) { reset(); return true }
                    addPoint(e.x, e.y)
                    // Wait a moment in case another stroke (or a second tap) follows
                    val delay = when {
                        hasDraw -> STROKE_GAP_MS
                        spec.waitForDoubleTap -> DOUBLE_TAP_MS
                        else -> 0L
                    }
                    if (delay == 0L) settle() else handler.postDelayed(settleRunnable, delay)
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(holdRunnable)
                    reset()
                }
            }
            return true
        }

        private fun addPoint(x: Float, y: Float) {
            strokes.lastOrNull()?.add(x to y)
            ink.lineTo(x, y)
        }

        /** Works out what the finished touch was. */
        private fun settle() {
            val done = strokes.map { it.toList() }
            reset()
            if (done.isEmpty()) return

            val isTap = { s: List<Pair<Float, Float>> ->
                val (x0, y0) = s.first()
                s.all { (x, y) -> hypot(x - x0, y - y0) <= slop }
            }
            if (done.all(isTap)) {
                onGesture(if (done.size >= 2) Trigger.DOUBLE_TAP else Trigger.SINGLE_TAP, null)
                return
            }

            if (hasDraw && pathLength(done) > 30 * density) {
                val shape = ShapeRecognizer.normalize(done)
                val match = shape?.let { ShapeRecognizer.recognize(it, spec.shapes, strictness) }
                if (match != null) {
                    onGesture(Trigger.DRAW, match)
                    return
                }
            }

            if (done.size == 1) swipeOf(done[0])?.let { onGesture(it, null) }
        }

        private fun swipeOf(s: List<Pair<Float, Float>>): Trigger? {
            val (x0, y0) = s.first()
            val (x1, y1) = s.last()
            val dx = x1 - x0
            val dy = y1 - y0
            val disp = hypot(dx, dy)
            if (disp < 40 * density) return null
            if (disp / pathLength(listOf(s)) < 0.7f) return null // too curvy to be a swipe
            return if (abs(dx) > abs(dy)) {
                if (dx > 0) Trigger.SWIPE_RIGHT else Trigger.SWIPE_LEFT
            } else {
                if (dy > 0) Trigger.SWIPE_DOWN else Trigger.SWIPE_UP
            }
        }

        private fun pathLength(strokes: List<List<Pair<Float, Float>>>): Float =
            strokes.sumOf { s ->
                s.zipWithNext { (ax, ay), (bx, by) -> hypot(bx - ax, by - ay).toDouble() }.sum()
            }.toFloat()

        private fun reset() {
            handler.removeCallbacks(settleRunnable)
            strokes.clear()
            ink.reset()
            invalidate()
        }

        override fun onDetachedFromWindow() {
            handler.removeCallbacksAndMessages(null)
            super.onDetachedFromWindow()
        }

        override fun onDraw(canvas: Canvas) {
            if (outlines) {
                val inset = outline.strokeWidth
                rect.set(inset, inset, width - inset, height - inset)
                canvas.drawRoundRect(rect, 12 * density, 12 * density, outline)
                drawIcons(canvas)
            }
            if (hasDraw && !ink.isEmpty) canvas.drawPath(ink, inkPaint)
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

        private companion object {
            const val HOLD_MS = 550L
            const val DOUBLE_TAP_MS = 280L
            /** Pause after lifting the finger before a drawing counts as finished. */
            const val STROKE_GAP_MS = 550L
        }
    }
}
