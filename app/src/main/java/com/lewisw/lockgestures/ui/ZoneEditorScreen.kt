package com.lewisw.lockgestures.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lewisw.lockgestures.model.SourceKind
import com.lewisw.lockgestures.model.Store
import com.lewisw.lockgestures.model.TouchZone
import com.lewisw.lockgestures.model.ZoneLayout
import com.lewisw.lockgestures.service.DeviceState
import kotlin.math.roundToInt

/**
 * Full-screen editor. The whole screen stands in for the lock screen, so a zone
 * drawn here lands in exactly the same place on the cover-screen lock screen.
 */
@Composable
fun ZoneEditorScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cs = MaterialTheme.colorScheme
    val zones = remember { mutableStateListOf<TouchZone>().apply { addAll(Store.config.value.zones) } }
    var selected by remember { mutableStateOf<Int?>(null) }
    var toolbarAtTop by remember { mutableStateOf(false) }
    val folded = remember { DeviceState(context).isFolded }
    val topInsetPx = WindowInsets.statusBars.getTop(density).toFloat()
    val bottomInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()

    BoxWithConstraints(Modifier.fillMaxSize().background(cs.surface)) {
        val wPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val topF = topInsetPx / hPx
        val botF = bottomInsetPx / hPx
        val minW = with(density) { 56.dp.toPx() } / wPx
        val minH = with(density) { 56.dp.toPx() } / hPx

        fun save() {
            val ids = zones.map { it.id }.toSet()
            Store.updateConfig { c ->
                c.copy(
                    zones = zones.toList(),
                    functions = c.functions.filter { f ->
                        f.source?.let { it.kind != SourceKind.TOUCH || it.zoneId in ids } ?: true
                    },
                    screenAspect = hPx / wPx,
                    topInset = topF,
                    bottomInset = botF,
                )
            }
            onDone()
        }
        BackHandler { save() }

        fun move(id: Int, dx: Float, dy: Float) {
            val i = zones.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
            zones[i] = ZoneLayout.move(zones[i], dx / wPx, dy / hPx, zones.toList(), topF, 1f - botF)
        }

        fun resize(id: Int, dx: Float, dy: Float) {
            val i = zones.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
            zones[i] = ZoneLayout.resize(zones[i], dx / wPx, dy / hPx, zones.toList(), minW, minH, 1f - botF)
        }

        // Tapping empty space deselects
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { selected = null } })

        // Areas used by the system (status bar at the top, navigation bar at the bottom)
        SystemBand(Modifier.fillMaxWidth().height(with(density) { topInsetPx.toDp() }).align(Alignment.TopCenter))
        SystemBand(Modifier.fillMaxWidth().height(with(density) { bottomInsetPx.toDp() }).align(Alignment.BottomCenter))

        // Instructions in the middle of the background
        Column(
            Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (zones.isEmpty()) "Tap Add to create a zone" else "Drag a zone to move it.\nDrag its corner to resize.\nZones can't overlap.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant.copy(alpha = 0.7f),
            )
            if (!folded) {
                Text(
                    "Tip: open this on the cover screen so the zones match it exactly.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        // Zones
        zones.forEachIndexed { index, z ->
            val isSel = selected == z.id
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .offset { IntOffset((z.left * wPx).roundToInt(), (z.top * hPx).roundToInt()) }
                    .size(with(density) { (z.width * wPx).toDp() }, with(density) { (z.height * hPx).toDp() })
                    .background(cs.primary.copy(alpha = if (isSel) 0.30f else 0.16f), shape)
                    .border(BorderStroke(if (isSel) 2.dp else 1.dp, cs.primary), shape)
                    .pointerInput(z.id) { detectTapGestures { selected = z.id } }
                    .pointerInput(z.id) {
                        detectDragGestures(onDragStart = { selected = z.id }) { change, drag ->
                            change.consume()
                            move(z.id, drag.x, drag.y)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Zone ${index + 1}", color = cs.primary, fontWeight = FontWeight.SemiBold)
                if (isSel) {
                    // Resize handle
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(30.dp)
                            .background(cs.primary, CircleShape)
                            .border(2.dp, cs.surface, CircleShape)
                            .pointerInput(z.id) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    resize(z.id, drag.x, drag.y)
                                }
                            }
                    )
                }
            }
        }

        // Toolbar (can be flipped to the top or bottom so it never hides a zone)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = cs.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(if (toolbarAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .padding(
                    top = if (toolbarAtTop) with(density) { topInsetPx.toDp() } + 12.dp else 0.dp,
                    bottom = if (!toolbarAtTop) with(density) { bottomInsetPx.toDp() } + 12.dp else 0.dp,
                ),
        ) {
            Row(
                Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(onClick = {
                    val id = (zones.maxOfOrNull { it.id } ?: 0) + 1
                    val spot = ZoneLayout.freeSpot(id, zones.toList(), topF, 1f - botF, minW, minH)
                    if (spot == null) {
                        Toast.makeText(context, "No free space for another zone", Toast.LENGTH_SHORT).show()
                    } else {
                        zones.add(spot)
                        selected = id
                    }
                }) { Icon(Icons.Filled.Add, "Add zone") }
                IconButton(
                    onClick = { zones.removeAll { it.id == selected }; selected = null },
                    enabled = selected != null,
                ) { Icon(Icons.Filled.Delete, "Delete zone") }
                IconButton(onClick = { toolbarAtTop = !toolbarAtTop }) {
                    Icon(
                        if (toolbarAtTop) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                        "Move toolbar",
                    )
                }
                Button(onClick = { save() }, modifier = Modifier.padding(start = 4.dp)) { Text("Done") }
            }
        }
    }
}

@Composable
private fun SystemBand(modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)))
}
