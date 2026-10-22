package nl.myquest.nowindcompanion.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ftdi.j2xx.D2xxManager
import nl.myquest.nowindcompanion.ConnectionState
import nl.myquest.nowindcompanion.DetectedNowindVersion
import nl.myquest.nowindcompanion.DeviceInfo
import nl.myquest.nowindcompanion.R

// D2XX encodes its version as hex digits, 0x21000000 is 2.10.00
fun formatD2xxVersion(version: Int): String =
    "%X.%02X.%02X".format((version ushr 28) and 0xF, (version ushr 20) and 0xFF, (version ushr 12) and 0xFF)

@Composable
fun NowindStatusScreen(
    connection: ConnectionState,
    device: DeviceInfo,
    hostedDisk: String?,
    onDebugLog: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val notConnected = "Not connected"
    ScreenScaffold("Nowind", onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            DeviceHeader(connection, device)

            Section("Interface") {
                InfoRow(Icons.Rounded.Numbers, "Serial number", device.serial.ifEmpty { notConnected })
                InfoRow(Icons.Rounded.Memory, "Description", device.description.ifEmpty { notConnected })
                InfoRow(Icons.Rounded.Code, "FTDI driver version", formatD2xxVersion(D2xxManager.getLibraryVersion()))
                InfoRow(Icons.Rounded.Save, "Hosted disk", hostedDisk ?: "None")
            }

            Section("This device") {
                InfoRow(Icons.Rounded.Android, "Android version", Build.VERSION.RELEASE)
                InfoRow(Icons.Rounded.Numbers, "SDK version", "API ${Build.VERSION.SDK_INT}")
                InfoRow(Icons.Rounded.Apps, "Application version", context.appVersionName())
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
private fun DeviceHeader(connection: ConnectionState, device: DeviceInfo) {
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
                Text(
                    if (device.version == DetectedNowindVersion.None) "No interface" else device.version.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                ConnectionChip(connection)
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
