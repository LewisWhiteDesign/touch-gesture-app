package com.lewisw.lockgestures.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lewisw.lockgestures.model.Shape
import com.lewisw.lockgestures.model.ShapeRecognizer
import kotlinx.coroutines.delay

private const val MAX_SAMPLES = 5
private const val RECOMMENDED = 3

/** Small drawing of a recorded shape. */
@Composable
fun ShapePreview(shape: Shape?, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier) {
        if (shape.isNullOrEmpty()) return@Canvas
        val minX = shape.minOf { it.x }; val maxX = shape.maxOf { it.x }
        val minY = shape.minOf { it.y }; val maxY = shape.maxOf { it.y }
        val span = maxOf(maxX - minX, maxY - minY, 0.0001f)
        val pad = size.minDimension * 0.15f
        val scale = (size.minDimension - pad * 2) / span
        val ox = (size.width - (maxX - minX) * scale) / 2 - minX * scale
        val oy = (size.height - (maxY - minY) * scale) / 2 - minY * scale
        shape.groupBy { it.stroke }.values.forEach { pts ->
            val path = Path()
            pts.forEachIndexed { i, p ->
                val x = p.x * scale + ox
                val y = p.y * scale + oy
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Lets the user teach the app a shape by drawing it a few times.
 * [others] are shapes already used by other functions on the same zone,
 * so we can warn if the new one is too similar.
 */
@Composable
fun ShapeRecorderDialog(
    initial: List<Shape>,
    others: Map<String, List<Shape>>,
    strictness: Int,
    onSave: (List<Shape>) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val samples = remember { mutableStateListOf<Shape>().apply { addAll(initial) } }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var lastLift by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    // Once the finger has been lifted for a moment, the drawing is finished.
    LaunchedEffect(lastLift) {
        if (lastLift == 0) return@LaunchedEffect
        delay(650)
        val raw = strokes.map { s -> s.map { it.x to it.y } }
        strokes.clear()
        val total = raw.sumOf { s -> s.zipWithNext { a, b -> Math.hypot((b.first - a.first).toDouble(), (b.second - a.second).toDouble()) }.sum() }
        val shape = if (total > 60) ShapeRecognizer.normalize(raw) else null
        if (shape == null) {
            message = "Too small — draw it bigger."
            return@LaunchedEffect
        }
        val clash = others.entries.firstOrNull { (_, s) ->
            s.isNotEmpty() && ShapeRecognizer.recognize(shape, mapOf(0 to s), strictness) != null
        }
        if (samples.size < MAX_SAMPLES) samples += shape
        message = when {
            clash != null -> "Looks a lot like the shape for ${clash.key}. Try something more different."
            samples.size < RECOMMENDED -> "Got it. Draw it ${RECOMMENDED - samples.size} more time(s)."
            else -> "Great — that's enough. You can add more for extra accuracy."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record shape") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Draw the shape in the box, the way you'll draw it on the lock screen. " +
                        "Do it $RECOMMENDED times so it's recognised reliably.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(cs.surfaceContainerHighest, RoundedCornerShape(16.dp))
                        .border(1.dp, cs.outlineVariant, RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                lastLift = 0
                                val pts = mutableListOf(down.position)
                                current = pts.toList()
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val c = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!c.pressed) break
                                    c.consume()
                                    pts += c.position
                                    current = pts.toList()
                                }
                                strokes += pts.toList()
                                current = emptyList()
                                lastLift = (System.nanoTime() and 0x7fffffff).toInt() or 1
                            }
                        },
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        (strokes + listOf(current)).forEach { s ->
                            if (s.size < 2) return@forEach
                            val path = Path().apply {
                                moveTo(s[0].x, s[0].y)
                                s.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(path, cs.primary, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
                Text(
                    message ?: "Sample ${samples.size + 1} of $RECOMMENDED",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (samples.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        samples.forEachIndexed { i, s ->
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .background(cs.secondaryContainer, RoundedCornerShape(8.dp))
                                    .clickable { samples.removeAt(i); message = "Removed sample ${i + 1}." },
                            ) {
                                ShapePreview(s, Modifier.matchParentSize(), cs.onSecondaryContainer)
                            }
                        }
                    }
                    Text("Tap a sample to remove it.", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                Box(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(samples.toList()) }, enabled = samples.isNotEmpty()) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { samples.clear(); strokes.clear(); message = null }) { Text("Start over") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
