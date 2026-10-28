package com.lewisw.lockgestures.model

import kotlin.math.max
import kotlin.math.min

/**
 * Keeps zones from overlapping. All values are fractions (0..1) of the screen.
 * Zones may touch edge to edge, but never overlap.
 */
object ZoneLayout {

    private const val EPS = 0.0005f

    private fun overlapsH(aL: Float, aR: Float, b: TouchZone) = aL < b.right - EPS && aR > b.left + EPS
    private fun overlapsV(aT: Float, aB: Float, b: TouchZone) = aT < b.bottom - EPS && aB > b.top + EPS

    fun overlaps(a: TouchZone, b: TouchZone) =
        a.id != b.id && overlapsH(a.left, a.right, b) && overlapsV(a.top, a.bottom, b)

    /**
     * Moves [z] by (dx, dy), sliding along other zones instead of passing through them.
     * Zones it already overlaps (e.g. from an older version) are ignored so it can be moved free.
     */
    fun move(
        z: TouchZone, dx: Float, dy: Float, all: List<TouchZone>,
        minTop: Float, maxBottom: Float,
    ): TouchZone {
        val others = all.filter { it.id != z.id && !overlaps(z, it) }
        val w = z.width
        val h = z.height

        // Horizontal step
        var l = (z.left + dx).coerceIn(0f, 1f - w)
        for (o in others) {
            if (!overlapsV(z.top, z.bottom, o)) continue
            if (dx > 0 && z.right <= o.left + EPS && l + w > o.left) l = min(l, o.left - w)
            if (dx < 0 && z.left >= o.right - EPS && l < o.right) l = max(l, o.right)
        }

        // Vertical step, using the new horizontal position
        var t = (z.top + dy).coerceIn(minTop, maxBottom - h)
        for (o in others) {
            if (!overlapsH(l, l + w, o)) continue
            if (dy > 0 && z.bottom <= o.top + EPS && t + h > o.top) t = min(t, o.top - h)
            if (dy < 0 && z.top >= o.bottom - EPS && t < o.bottom) t = max(t, o.bottom)
        }

        val moved = TouchZone(z.id, l, t, l + w, t + h)
        return if (others.any { overlaps(moved, it) }) z else moved
    }

    /** Resizes [z] from its bottom-right corner, stopping at other zones. */
    fun resize(
        z: TouchZone, dx: Float, dy: Float, all: List<TouchZone>,
        minW: Float, minH: Float, maxBottom: Float,
    ): TouchZone {
        val others = all.filter { it.id != z.id && !overlaps(z, it) }

        var r = (z.right + dx).coerceIn(z.left + minW, 1f)
        for (o in others) {
            if (overlapsV(z.top, z.bottom, o) && o.left >= z.right - EPS && r > o.left) r = min(r, o.left)
        }
        var b = (z.bottom + dy).coerceIn(z.top + minH, maxBottom)
        for (o in others) {
            if (overlapsH(z.left, r, o) && o.top >= z.bottom - EPS && b > o.top) b = min(b, o.top)
        }

        val resized = z.copy(right = max(r, z.left + minW), bottom = max(b, z.top + minH))
        return if (others.any { overlaps(resized, it) }) z else resized
    }

    /**
     * Finds an empty spot for a new zone, trying a few sizes from large to small.
     * Returns null when the screen is full.
     */
    fun freeSpot(
        id: Int, all: List<TouchZone>, minTop: Float, maxBottom: Float,
        minW: Float, minH: Float,
    ): TouchZone? {
        val sizes = listOf(0.5f to 0.12f, 0.4f to 0.09f, 0.3f to 0.07f, minW to minH)
        for ((w0, h0) in sizes) {
            val w = max(w0, minW)
            val h = max(h0, minH)
            // Prefer the middle of the screen, then work outwards
            val ys = generateSequence(0f) { it + 0.01f }.takeWhile { it <= 1f }
                .map { minTop + it * (maxBottom - minTop - h) }
                .filter { it >= minTop && it + h <= maxBottom }
                .sortedBy { kotlin.math.abs(it + h / 2 - 0.45f) }
                .toList()
            val xs = listOf((1f - w) / 2f) + generateSequence(0f) { it + 0.02f }
                .takeWhile { it + w <= 1f }.toList()
            for (y in ys) for (x in xs) {
                val c = TouchZone(id, x, y, x + w, y + h)
                if (all.none { overlaps(c, it) }) return c
            }
        }
        return null
    }
}
