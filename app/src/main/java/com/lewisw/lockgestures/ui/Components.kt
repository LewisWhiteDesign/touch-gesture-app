package com.lewisw.lockgestures.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lewisw.lockgestures.model.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A small drawing of the cover screen showing where the zones are. */
@Composable
fun DevicePreview(config: Config, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    BoxWithConstraints(
        modifier
            .aspectRatio(1f / config.screenAspect)
            .border(BorderStroke(2.dp, cs.outline), shape)
            .background(cs.surfaceContainerHighest, shape)
    ) {
        val w = maxWidth
        val h = maxHeight
        // System areas (status bar / navigation bar) can't hold zones
        Box(
            Modifier.fillMaxWidth().height(h * config.topInset)
                .background(cs.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
        )
        Box(
            Modifier.fillMaxWidth().height(h * config.bottomInset)
                .offset(y = h * (1f - config.bottomInset))
                .background(cs.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp))
        )
        config.zones.forEachIndexed { i, z ->
            Box(
                Modifier
                    .offset(x = w * z.left, y = h * z.top)
                    .size(w * z.width, h * z.height)
                    .background(cs.primary.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                    .border(1.dp, cs.primary, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("${i + 1}", color = cs.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        if (config.zones.isEmpty()) {
            Text(
                "No zones",
                Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

/** A compact dropdown field used in each Functions row. */
@Composable
fun DropField(
    value: String?,
    placeholder: String,
    options: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Box(modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (value == null) cs.surfaceContainerHigh else cs.secondaryContainer,
            border = if (highlight) BorderStroke(1.5.dp, cs.error) else null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable { open = true },
        ) {
            Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                Text(
                    value ?: placeholder,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (value == null) cs.onSurfaceVariant else cs.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, pick) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; pick() })
            }
        }
    }
}

data class AppEntry(val label: String, val pkg: String)

/** Lists launchable apps for the "Open App" action. */
@Composable
fun AppPickerDialog(onPick: (AppEntry) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
                .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.pkg != context.packageName }
                .distinctBy { it.pkg }
                .sortedBy { it.label.lowercase() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Choose app") },
        text = {
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(list, key = { it.pkg }) { app ->
                        Text(
                            app.label,
                            Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
    )
}

