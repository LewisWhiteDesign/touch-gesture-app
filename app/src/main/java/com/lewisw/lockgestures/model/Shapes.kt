package com.lewisw.lockgestures.model

import org.json.JSONArray
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** One point of a drawn shape. [stroke] says which pen-stroke it belongs to. */
data class ShapePoint(val x: Float, val y: Float, val stroke: Int)

/** A normalised drawing (fixed number of points, centred, scaled to size 1). */
typealias Shape = List<ShapePoint>

/**
 * Recognises drawn shapes using the "$P point-cloud" method
 * (Vatavu, Anthony & Wobbrock, 2012). It copes with shapes drawn in several
 * strokes and in any stroke order/direction, but does care about orientation,
 * so "N" and "Z" stay different.
 */
object ShapeRecognizer {

    private const val N = 48

    /** Turns raw strokes (screen points) into a comparable [Shape], or null if too small. */
    fun normalize(strokes: List<List<Pair<Float, Float>>>): Shape? {
        val raw = strokes.flatMapIndexed { i, s -> s.map { (x, y) -> ShapePoint(x, y, i) } }
        if (raw.size < 2) return null
        val minX = raw.minOf { it.x }; val maxX = raw.maxOf { it.x }
        val minY = raw.minOf { it.y }; val maxY = raw.maxOf { it.y }
        val size = max(maxX - minX, maxY - minY)
        if (size <= 0f) return null
        val scaled = raw.map { ShapePoint((it.x - minX) / size, (it.y - minY) / size, it.stroke) }
        val resampled = resample(scaled, N)
        val cx = resampled.sumOf { it.x.toDouble() }.toFloat() / resampled.size
        val cy = resampled.sumOf { it.y.toDouble() }.toFloat() / resampled.size
        return resampled.map { ShapePoint(it.x - cx, it.y - cy, it.stroke) }
    }

    /** Average distance between two shapes, as a fraction of the shape size. Lower = more alike. */
    fun distance(a: Shape, b: Shape): Float {
        val n = min(a.size, b.size)
        if (n == 0) return Float.MAX_VALUE
        val step = max(1, floor(n.toDouble().pow(0.5)).toInt())
        var best = Float.MAX_VALUE
        var i = 0
        while (i < n) {
            best = min(best, min(cloudDistance(a, b, i, n), cloudDistance(b, a, i, n)))
            i += step
        }
        return best
    }

    private fun cloudDistance(pts: Shape, tmpl: Shape, start: Int, n: Int): Float {
        val matched = BooleanArray(n)
        var sum = 0f
        var weights = 0f
        var i = start
        do {
            var minD = Float.MAX_VALUE
            var index = -1
            for (j in 0 until n) {
                if (matched[j]) continue
                val dx = pts[i].x - tmpl[j].x
                val dy = pts[i].y - tmpl[j].y
                val d = sqrt(dx * dx + dy * dy)
                if (d < minD) { minD = d; index = j }
            }
            matched[index] = true
            val weight = 1f - ((i - start + n) % n).toFloat() / n
            sum += weight * minD
            weights += weight
            i = (i + 1) % n
        } while (i != start)
        return sum / weights
    }

    private fun resample(points: Shape, n: Int): Shape {
        val total = pathLength(points)
        if (total <= 0f) return List(n) { points.first() }
        val interval = total / (n - 1)
        var acc = 0f
        val src = points.toMutableList()
        val out = mutableListOf(src[0])
        var i = 1
        while (i < src.size) {
            val p = src[i - 1]; val q = src[i]
            if (p.stroke == q.stroke) {
                val d = dist(p, q)
                if (acc + d >= interval && d > 0f) {
                    val t = (interval - acc) / d
                    val r = ShapePoint(p.x + t * (q.x - p.x), p.y + t * (q.y - p.y), p.stroke)
                    out += r
                    src.add(i, r)
                    acc = 0f
                } else acc += d
            }
            i++
        }
        while (out.size < n) out += src.last()
        return out.take(n)
    }

    private fun pathLength(points: Shape): Float {
        var d = 0f
        for (i in 1 until points.size) if (points[i].stroke == points[i - 1].stroke) d += dist(points[i - 1], points[i])
        return d
    }

    private fun dist(a: ShapePoint, b: ShapePoint): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * How close a drawing must be to count as a match. Looser when the user's own
     * recordings vary a lot, so a sloppy-but-consistent writer still gets matches.
     * [strictness]: 0 = loose, 1 = normal, 2 = strict.
     */
    fun threshold(samples: List<Shape>, strictness: Int): Float {
        // Tuned by simulation: at "normal", ~98% of redraws match the right shape and
        // ~3% of other shapes are wrongly accepted when only one shape is set up.
        val base = when (strictness) { 0 -> 0.11f; 2 -> 0.075f; else -> 0.09f }
        if (samples.size < 2) return base
        var worst = 0f
        for (i in samples.indices) for (j in i + 1 until samples.size) {
            worst = max(worst, distance(samples[i], samples[j]))
        }
        return worst.coerceIn(base, base * 1.2f)
    }

    /** Best-scoring candidate id for [drawing], or null if nothing is close enough. */
    fun <K> recognize(drawing: Shape, candidates: Map<K, List<Shape>>, strictness: Int): K? {
        var bestKey: K? = null
        var bestRatio = Float.MAX_VALUE
        for ((key, samples) in candidates) {
            if (samples.isEmpty()) continue
            val d = samples.minOf { distance(drawing, it) }
            val ratio = d / threshold(samples, strictness)
            if (ratio < bestRatio) { bestRatio = ratio; bestKey = key }
        }
        return if (bestRatio <= 1f) bestKey else null
    }

    // ---- Saving ----------------------------------------------------------

    fun toJson(samples: List<Shape>): JSONArray = JSONArray().apply {
        samples.forEach { s ->
            put(JSONArray().apply {
                s.forEach { p ->
                    put(Math.round(p.x * 10000) / 10000.0)
                    put(Math.round(p.y * 10000) / 10000.0)
                    put(p.stroke)
                }
            })
        }
    }

    fun fromJson(arr: JSONArray?): List<Shape> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONArray(i) ?: return@mapNotNull null
            (0 until a.length() / 3).map { k ->
                ShapePoint(a.getDouble(k * 3).toFloat(), a.getDouble(k * 3 + 1).toFloat(), a.getInt(k * 3 + 2))
            }.takeIf { it.isNotEmpty() }
        }
    }
}
