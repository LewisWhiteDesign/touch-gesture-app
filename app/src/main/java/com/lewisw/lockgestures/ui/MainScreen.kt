package com.lewisw.lockgestures.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lewisw.lockgestures.model.Action
import com.lewisw.lockgestures.model.Config
import com.lewisw.lockgestures.model.GestureFunction
import com.lewisw.lockgestures.model.SourceKind
import com.lewisw.lockgestures.model.Store
import com.lewisw.lockgestures.model.Trigger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(resumeTick: Int, onSetZones: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val config by Store.config.collectAsState()
    val settings by Store.settings.collectAsState()
    val lastEvent by Store.lastEvent.collectAsState()
    val serviceOn = remember(resumeTick) { Permissions.accessibilityOn(context) }
    var confirmClear by remember { mutableStateOf(false) }
    var pickAppFor by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lock Gestures", fontWeight = FontWeight.SemiBold) },
                actions = {
                    Switch(
                        checked = settings.enabled,
                        onCheckedChange = { on -> Store.updateSettings { it.copy(enabled = on) } },
                    )
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!serviceOn) {
                item { ServiceOffBanner(onClick = onSettings) }
            }

            // ---------------- Zones ----------------
            item {
                SectionCard(title = "Zones") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DevicePreview(config, Modifier.width(96.dp))
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onSetZones, modifier = Modifier.fillMaxWidth()) { Text("Set") }
                            OutlinedButton(
                                onClick = { confirmClear = true },
                                enabled = config.zones.isNotEmpty() || config.functions.isNotEmpty(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Clear") }
                            Text(
                                when (config.zones.size) {
                                    0 -> "Tap Set to draw tap/swipe zones on the cover screen."
                                    1 -> "1 zone"
                                    else -> "${config.zones.size} zones"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ---------------- Functions ----------------
            item {
                SectionCard(title = "Functions") {
                    if (config.functions.isEmpty()) {
                        Text(
                            "No functions yet. Tap + to link a trigger and zone to an action.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    } else {
                        Row(Modifier.fillMaxWidth().padding(end = 38.dp, bottom = 6.dp)) {
                            listOf("Trigger", "Zone", "Action").forEach {
                                Text(
                                    it, Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        config.functions.forEachIndexed { index, f ->
                            key(f.id) {
                                FunctionRow(
                                    f = f,
                                    config = config,
                                    showDivider = index > 0,
                                    onChange = { updated ->
                                        Store.updateConfig { c -> c.copy(functions = c.functions.map { if (it.id == f.id) updated else it }) }
                                    },
                                    onDelete = { Store.updateConfig { c -> c.copy(functions = c.functions.filterNot { it.id == f.id }) } },
                                    onPickApp = { pickAppFor = f.id },
                                )
                            }
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                        FilledTonalButton(onClick = {
                            Store.updateConfig { c -> c.copy(functions = c.functions + GestureFunction(id = System.currentTimeMillis())) }
                        }) {
                            Icon(Icons.Filled.Add, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add function")
                        }
                    }
                }
            }

            item {
                Text(
                    "Last gesture: $lastEvent",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear everything?") },
            text = { Text("This removes all zones and all functions.") },
            confirmButton = {
                TextButton(onClick = {
                    Store.updateConfig { it.copy(zones = emptyList(), functions = emptyList()) }
                    confirmClear = false
                }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    pickAppFor?.let { id ->
        AppPickerDialog(
            onPick = { app ->
                Store.updateConfig { c ->
                    c.copy(functions = c.functions.map {
                        if (it.id == id) it.copy(appPackage = app.pkg, appLabel = app.label) else it
                    })
                }
                pickAppFor = null
            },
            onDismiss = { pickAppFor = null },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ServiceOffBanner(onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Gesture service is off", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text("Tap to finish setup", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun FunctionRow(
    f: GestureFunction,
    config: Config,
    showDivider: Boolean,
    onChange: (GestureFunction) -> Unit,
    onDelete: () -> Unit,
    onPickApp: () -> Unit,
) {
    val kind = f.source?.kind
    val duplicate = f.isComplete && config.functions.count {
        it.isComplete && it.trigger == f.trigger && it.source == f.source
    } > 1

    // Only offer combinations that make sense together.
    val triggerOptions = (kind?.validTriggers() ?: Trigger.entries).map { t ->
        DropOption(t.labelFor(kind)) { onChange(f.copy(trigger = t)) }
    }
    val sourceOptions = config.allSources()
        .filter { s -> f.trigger == null || f.trigger in s.kind.validTriggers() }
        .map { s -> DropOption(config.sourceLabel(s)) { onChange(f.copy(source = s)) } }
    val actionOptions = Action.entries.map { a ->
        DropOption(a.label, a.icon) {
            onChange(if (a == Action.OPEN_APP) f.copy(action = a) else f.copy(action = a, appPackage = null, appLabel = null))
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        if (showDivider) HorizontalDivider(Modifier.padding(bottom = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DropField(f.trigger?.labelFor(kind), "Trigger", triggerOptions, Modifier.weight(1f), highlight = duplicate)
            DropField(f.source?.let { config.sourceLabel(it) }, "Zone", sourceOptions, Modifier.weight(1f), highlight = duplicate)
            DropField(f.action?.short, "Action", actionOptions, Modifier.weight(1f), valueIcon = f.action?.icon)
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (f.action == Action.OPEN_APP) {
            TextButton(onClick = onPickApp) {
                Text(f.appLabel?.let { "App: $it (tap to change)" } ?: "Choose which app to open")
            }
        }
        val hint = when {
            duplicate -> "Another function uses the same trigger and zone — both will run."
            kind != null && kind.isGyro -> kind.help
            f.action?.needsUnlock == true -> "Asks for your PIN first."
            f.action?.tileLabels?.isNotEmpty() == true -> "Taps the Quick Settings tile for you — no unlock needed."
            kind == SourceKind.TOUCH && config.zones.none { it.id == f.source?.zoneId } -> "This zone no longer exists."
            else -> null
        }
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = if (duplicate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, start = 2.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
    }
}
