package nl.myquest.nowindcompanion.ui

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ftdi.j2xx.D2xxManager
import kotlinx.coroutines.launch
import nl.myquest.nowindcompanion.ConnectionState
import nl.myquest.nowindcompanion.DetectedNowindVersion
import nl.myquest.nowindcompanion.DeviceInfo
import nl.myquest.nowindcompanion.R
import nl.myquest.nowindcompanion.TransferStats
import nl.myquest.nowindcompanion.formatSize
import nl.myquest.nowindcompanion.formatSpeed

// D2XX encodes its version as hex digits, 0x21000000 is 2.10.00
fun formatD2xxVersion(version: Int): String =
    "%X.%02X.%02X".format((version ushr 28) and 0xF, (version ushr 20) and 0xFF, (version ushr 12) and 0xFF)

private data class Info(val icon: ImageVector, val label: String, val value: String)

private fun statsLines(title: String, stats: TransferStats): List<String> =
    if (stats.count == 0) listOf("$title: no transfers yet")
    else listOf(
        "$title: ${stats.count} transfers, ${formatSize(stats.totalBytes)}",
        "  Average: ${formatSpeed(stats.mean)}",
        "  Maximum: ${formatSpeed(stats.maximum)}",
        "  Standard deviation: ${formatSpeed(stats.standardDeviation)}",
    )

@Composable
fun NowindStatusScreen(
    connection: ConnectionState,
    device: DeviceInfo,
    hostedDisk: String?,
    readStats: TransferStats,
    writeStats: TransferStats,
    onResetStats: () -> Unit,
    onDebugLog: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notConnected = "Not connected"
    val deviceName = if (device.version == DetectedNowindVersion.None) "No interface" else device.version.toString()

    val interfaceInfo = listOf(
        Info(Icons.Rounded.Numbers, "Serial number", device.serial.ifEmpty { notConnected }),
        Info(Icons.Rounded.Memory, "Description", device.description.ifEmpty { notConnected }),
        Info(Icons.Rounded.Code, "FTDI driver version", formatD2xxVersion(D2xxManager.getLibraryVersion())),
        Info(Icons.Rounded.Save, "Hosted disk", hostedDisk ?: "None"),
    )
    val deviceInfo = listOf(
        Info(Icons.Rounded.Android, "Android version", Build.VERSION.RELEASE),
        Info(Icons.Rounded.Numbers, "SDK version", "API ${Build.VERSION.SDK_INT}"),
        Info(Icons.Rounded.Apps, "Application version", context.appVersionName()),
        Info(Icons.Rounded.Android, "Device", "${Build.MANUFACTURER} ${Build.MODEL}"),
    )

    // everything on the page as plain text, for bug reports
    fun pageText(): String = buildList {
        add("Nowind Companion status")
        add("$deviceName: ${connection.label}")
        add("")
        add("Interface")
        interfaceInfo.forEach { add("  ${it.label}: ${it.value}") }
        add("")
        add("This device")
        deviceInfo.forEach { add("  ${it.label}: ${it.value}") }
        add("")
        add("Transfer speed")
        statsLines("  Read", readStats).forEach { add(it) }
        statsLines("  Write", writeStats).forEach { add(it) }
    }.joinToString("\n")

    ScreenScaffold(
        "Nowind",
        onBack,
        actions = {
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(pageText()))
                // Android 13+ shows its own confirmation when something is copied
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    scope.launch { snackbar.showSnackbar("Status copied to the clipboard") }
                }
            }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy all information")
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            DeviceHeader(connection, deviceName)

            Section("Interface") {
                interfaceInfo.forEach { InfoRow(it) }
            }

            Section("Transfer speed") {
                StatsRow(Icons.Rounded.Download, "Read", readStats)
                StatsRow(Icons.Rounded.Upload, "Write", writeStats)
                TextButton(
                    onClick = onResetStats,
                    enabled = readStats.count > 0 || writeStats.count > 0,
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("Reset") }
            }

            Section("This device") {
                deviceInfo.forEach { InfoRow(it) }
            }

            FilledTonalButton(onClick = onDebugLog, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Terminal, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Debug log")
            }
        }
    }
}

@Composable
private fun DeviceHeader(connection: ConnectionState, deviceName: String) {
    ElevatedCard(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.nowind),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(deviceName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                ConnectionChip(connection)
            }
        }
    }
}

// one line with label and value; a value that does not fit can be tapped to show it completely
@Composable
private fun InfoRow(info: Info) {
    var expanded by rememberSaveable(info.label) { mutableStateOf(false) }
    var overflows by remember(info.value) { mutableStateOf(false) }
    val expandable = overflows || expanded
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = expandable) { expanded = !expanded }
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = if (expanded) Alignment.Top else Alignment.CenterVertically
    ) {
        Icon(info.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        if (expanded) {
            Column(Modifier.weight(1f)) {
                Text(info.label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    info.value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(info.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(
                info.value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { overflows = it.hasVisualOverflow },
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        if (expandable) {
            Icon(
                if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Show less" else "Show all",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun StatsRow(icon: ImageVector, title: String, stats: TransferStats) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (stats.count == 0) "No transfers yet"
                else "${stats.count} × · ${formatSize(stats.totalBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (stats.count > 0) {
            Spacer(Modifier.size(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Average", formatSpeed(stats.mean), Modifier.weight(1f))
                StatTile("Maximum", formatSpeed(stats.maximum), Modifier.weight(1f))
                StatTile("Std. dev.", formatSpeed(stats.standardDeviation), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
