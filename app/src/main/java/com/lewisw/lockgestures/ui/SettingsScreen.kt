package com.lewisw.lockgestures.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lewisw.lockgestures.model.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(resumeTick: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by Store.settings.collectAsState()
    val accessibility = remember(resumeTick) { Permissions.accessibilityOn(context) }
    val dnd = remember(resumeTick) { Permissions.dndAccess(context) }
    val write = remember(resumeTick) { Permissions.writeSettings(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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
            item {
                Group("Setup") {
                    PermissionRow(
                        "Gesture service",
                        "Required. Settings → Accessibility → Installed apps → Lock Gestures. " +
                            "If it's greyed out, open App info → ⋮ → Allow restricted settings first.",
                        accessibility,
                    ) { Permissions.openAccessibility(context) }
                    TextButton(onClick = { Permissions.openAppInfo(context) }) { Text("Open App info") }
                    HorizontalDivider()
                    PermissionRow(
                        "Silent mode access",
                        "Needed for full Silent mode (Do Not Disturb access).",
                        dnd,
                    ) { Permissions.openDnd(context) }
                    HorizontalDivider()
                    PermissionRow(
                        "Brightness control",
                        "Needed for Brightness up/down (Modify system settings).",
                        write,
                    ) { Permissions.openWriteSettings(context) }
                }
            }

            item {
                Group("When gestures work") {
                    ToggleRow("Only when folded", "Ignore gestures on the inner screen", settings.onlyWhenFolded) { on ->
                        Store.updateSettings { it.copy(onlyWhenFolded = on) }
                    }
                    ToggleRow("Only on the lock screen", "Turn off to test while unlocked", settings.onlyWhenLocked) { on ->
                        Store.updateSettings { it.copy(onlyWhenLocked = on) }
                    }
                    ToggleRow("Show zone outlines", "Faint dashed outlines on the lock screen", settings.showZoneOutlines) { on ->
                        Store.updateSettings { it.copy(showZoneOutlines = on) }
                    }
                    Text("Motion sensitivity", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Low", "Medium", "High").forEachIndexed { i, label ->
                            FilterChip(
                                selected = settings.motionSensitivity == i,
                                onClick = { Store.updateSettings { it.copy(motionSensitivity = i) } },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text("Shape matching", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Loose", "Normal", "Strict").forEachIndexed { i, label ->
                            FilterChip(
                                selected = settings.drawStrictness == i,
                                onClick = { Store.updateSettings { it.copy(drawStrictness = i) } },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text(
                        "Loose accepts sloppier drawings but may trigger by accident. Strict needs a closer match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Group("Samsung tips") {
                    Tip("Quick Settings actions (Wi-Fi, Bluetooth, Airplane, Hotspot, NFC, Power saving) work by tapping the tile for you. Keep those tiles on the first page of your Quick Settings panel.")
                    Tip("Settings → Lock screen and AOD → Secure lock settings → turn off \"Lock network and security\", or Samsung blocks those tiles while locked.")
                    Tip("Settings → Battery → Background usage limits → Never sleeping apps → add Lock Gestures.")
                }
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun PermissionRow(title: String, desc: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            null,
            tint = if (granted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (granted) "Allowed" else desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Tip(text: String) {
    Text(
        "• $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
