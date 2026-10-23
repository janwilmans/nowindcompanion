package nl.myquest.nowindcompanion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import nl.myquest.nowindcompanion.DiskSource
import nl.myquest.nowindcompanion.SourceKind
import nl.myquest.nowindcompanion.Settings

@Composable
fun SettingsScreen(
    settings: Settings,
    onEnableDos2: (Boolean) -> Unit,
    onDebugEnabled: (Boolean) -> Unit,
    onKeepActive: (Boolean) -> Unit,
    onSourceEnabled: (DiskSource, Boolean) -> Unit,
    onRemoveSource: (DiskSource) -> Unit,
    onAddSource: (DiskSource) -> Unit,
    onEditDefaultSource: (String, SourceKind) -> Unit,
    onFirmware: () -> Unit,
    onBack: () -> Unit,
) {
    // the hosting notification needs permission on Android 13+, without it the service still runs, just invisibly
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onKeepActive(true)
    }
    val context = LocalContext.current
    val enableKeepActive: (Boolean) -> Unit = { enable ->
        val needsPermission = enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onKeepActive(enable)
        }
    }

    ScreenScaffold("Settings", onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Section("Hosting") {
                SwitchRow(
                    icon = Icons.Rounded.PowerSettingsNew,
                    title = "Keep hosting in the background",
                    description = "While the interface is connected, keeps the app active with a notification, so the MSX can use its disk while " +
                            "the screen is off or another app is open. Uses more battery.",
                    checked = settings.keepActive,
                    onCheckedChange = enableKeepActive
                )
            }
            Section("Disk image sources") {
                SourceRows(
                    settings.allSources, settings.defaultSourceName,
                    onSourceEnabled, onRemoveSource, onAddSource, onEditDefaultSource
                )
            }
            Section("MSX") {
                SwitchRow(
                    icon = Icons.Rounded.Dns,
                    title = "Enable DOS2",
                    description = "Boot MSX-DOS2 on MSX2 and newer. Some games do not work with DOS2, " +
                            "takes effect at the next MSX reset.",
                    checked = settings.enableDos2,
                    onCheckedChange = onEnableDos2
                )
            }
            Section("Interface") {
                NavigationRow(
                    icon = Icons.Rounded.Memory,
                    title = "Firmware update",
                    description = "Verify or update the firmware in the flash rom of the Nowind interface.",
                    onClick = onFirmware
                )
            }
            Section("Diagnostics") {
                SwitchRow(
                    icon = Icons.Rounded.BugReport,
                    title = "Enable debug information",
                    description = "Log protocol traffic and messages. When disabled, all debug output is turned off.",
                    checked = settings.debugEnabled,
                    onCheckedChange = onDebugEnabled
                )
            }
        }
    }
}

@Composable
private fun NavigationRow(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        // the whole row is toggleable, so the switch itself does not handle clicks
        Switch(checked = checked, onCheckedChange = null)
    }
}
