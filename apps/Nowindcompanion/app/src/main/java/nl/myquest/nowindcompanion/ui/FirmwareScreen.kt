package nl.myquest.nowindcompanion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.myquest.nowindcompanion.ConnectionState
import nl.myquest.nowindcompanion.APP_FOLDER
import nl.myquest.nowindcompanion.EraseMethod
import nl.myquest.nowindcompanion.FirmwareStatus
import nl.myquest.nowindcompanion.FirmwareUpdater
import nl.myquest.nowindcompanion.downloadFirmware
import nl.myquest.nowindcompanion.expandFirmwareName
import nl.myquest.nowindcompanion.findFirmwareVersions
import nl.myquest.nowindcompanion.formatSize

@Composable
fun FirmwareScreen(
    firmware: FirmwareUpdater,
    connection: ConnectionState,
    firmwareSource: String,
    onFirmwareSource: (String) -> Unit,
    eraseMethod: EraseMethod,
    onEraseMethod: (EraseMethod) -> Unit,
    storageReady: Boolean,      // backups need the storage folder
    onBack: () -> Unit,
) {
    val status by firmware.status.collectAsState()
    val localFile by firmware.localFile.collectAsState()
    val useLocalFile by firmware.useLocalFile.collectAsState()
    val fileInfo by firmware.fileInfo.collectAsState()
    val cache by firmware.cache.collectAsState()
    var confirming by remember { mutableStateOf(false) }
    var editingLink by remember { mutableStateOf(false) }
    val busy = status is FirmwareStatus.Busy
    val connected = connection == ConnectionState.Hosting
    val canStart = connected && !busy && (useLocalFile || firmwareSource.isNotBlank())

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { firmware.pickLocalFile(it) }
    }

    // the screen going off must not pause the app halfway an update
    val view = LocalView.current
    DisposableEffect(busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }

    ScreenScaffold("Firmware update", onBack) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Section("Instructions") {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1.  Turn on the MSX (or reset it)", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "2.  Hold the F key and switch on the MSX. Keep holding it until the screen turns red " +
                                "and shows “Nowind Flash Writer Mode”.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Section("Firmware") {
                ChoiceRow(selected = !useLocalFile, enabled = !busy, onClick = { firmware.useLocalFile(false) }) {
                    Column(Modifier.weight(1f)) {
                        Text("Download", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (firmwareSource.isBlank()) "No link set" else expandFirmwareName(firmwareSource),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { editingLink = true }, enabled = !busy) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit the firmware link")
                    }
                }
                ChoiceRow(
                    selected = useLocalFile,
                    enabled = !busy && localFile != null,
                    onClick = { firmware.useLocalFile(true) }
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("File on this phone", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            localFile?.let { "${it.name}  ·  ${formatSize(it.data.size.toLong())}" } ?: "No file chosen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) { Text("Choose") }
                }
            }

            Section("Erase method") {
                ChoiceRow(selected = eraseMethod == EraseMethod.Chip, enabled = !busy, onClick = { onEraseMethod(EraseMethod.Chip) }) {
                    ChoiceText("Whole flash rom", "Works on every interface. Takes a few minutes and also erases a ROM disk.")
                }
                ChoiceRow(selected = eraseMethod == EraseMethod.Sector, enabled = !busy, onClick = { onEraseMethod(EraseMethod.Sector) }) {
                    ChoiceText("Only the sectors that are written", "Faster, like 'nwhostapp --glash'.")
                }
                Text(
                    "Use 'only the sectors' on the V2 small form factor interface only, the V1 interface does not support it.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (eraseMethod == EraseMethod.Sector) FontWeight.SemiBold else null,
                    color = if (eraseMethod == EraseMethod.Sector) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                )
            }

            StatusText(status, connected)
            fileInfo?.let { Section("In the firmware file") { InfoTable(it) } }
            // the last results of the buttons below, until a button is pressed again
            if (status !is FirmwareStatus.Report) cache.version?.let { Section("Version (last check)") { InfoTable(it) } }
            cache.verify?.let { Section("Last verify") { InfoTable(it) } }
            cache.written?.let { Section("Last firmware update") { InfoTable(it) } }

            Text(
                "Check version reports the interface version, its flash rom, the firmware version and the USB details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (storageReady) "Back up saves the firmware of the interface in Download/$APP_FOLDER/firmware as nowind-YYMMDD-HHmmss.rom. " +
                        "To restore it, choose that file as 'File on this phone' and update."
                else "Backups are saved in Download/$APP_FOLDER, choose that storage folder first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Verify only checks whether the interface already has this firmware, it changes nothing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = firmware::checkVersion, enabled = connected && !busy, modifier = Modifier.weight(1f)) {
                    Text("Check version")
                }
                OutlinedButton(onClick = firmware::backup, enabled = connected && !busy && storageReady, modifier = Modifier.weight(1f)) {
                    Text("Back up")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = firmware::verify, enabled = canStart, modifier = Modifier.weight(1f)) {
                    Text("Verify")
                }
                Button(onClick = { confirming = true }, enabled = canStart, modifier = Modifier.weight(1f)) {
                    Text("Update")
                }
            }
        }
    }

    if (editingLink) {
        FirmwareLinkDialog(
            initial = firmwareSource,
            onDismiss = { editingLink = false },
            onSave = {
                onFirmwareSource(it)
                firmware.useLocalFile(false)
                editingLink = false
            }
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Update the firmware?") },
            text = {
                Text(
                    when (eraseMethod) {
                        EraseMethod.Chip -> "The whole flash rom of the interface is erased and written again, this takes a few minutes. "
                        EraseMethod.Sector -> "Only the sectors of the flash rom that are written are erased. " +
                                "This only works on the V2 small form factor interface, not on V1. "
                    } + "Do not switch off the MSX or disconnect the phone until it is done."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    firmware.update()
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Cancel") }
            }
        )
    }
}

// asks for the firmware link and checks it before saving, like adding a disk image source:
// the file is downloaded and must look like Nowind firmware
@Composable
private fun FirmwareLinkDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var checking by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val url = if (text.isBlank()) "" else expandFirmwareName(text)

    fun check() {
        if (text.isBlank()) return
        val checkedUrl = url
        checking = true
        error = null
        scope.launch {
            try {
                val rom = withContext(Dispatchers.IO) { downloadFirmware(checkedUrl) }
                val versions = findFirmwareVersions(rom)
                found = listOfNotNull(versions.firmware, versions.flashWriter, formatSize(rom.size.toLong())).joinToString("  ·  ")
            } catch (e: Exception) {
                error = e.message ?: e.toString()
            } finally {
                checking = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Firmware link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The url of a Nowind firmware file. A name without dots stands for " +
                            "https://<name>.nl/nowind/files/nowind.rom.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        found = null
                        error = null
                    },
                    label = { Text("URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                // a short name stands for a longer url, show which one
                if (url.isNotEmpty() && url != text.trim()) {
                    Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Checking…")
                    }

                    found != null -> Text("Found firmware: $found", color = MaterialTheme.colorScheme.primary)
                    error != null -> Text("Could not use it: $error", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            when {
                found != null -> TextButton(onClick = { onSave(text) }) { Text("Save") }
                // the site may be down right now; keep the link, verify and update check the file again anyway
                error != null -> TextButton(onClick = { onSave(text) }) { Text("Save anyway") }
                else -> TextButton(onClick = { check() }, enabled = text.isNotBlank() && !checking) { Text("Check") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // the whole row is selectable, so the radio button itself does not handle clicks
        RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.padding(12.dp))
        Spacer(Modifier.width(4.dp))
        content()
    }
}

@Composable
private fun RowScope.ChoiceText(title: String, description: String) {
    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoTable(items: List<Pair<String, String>>) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((label, value) in items) {
            Row {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.4f)
                )
                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
            }
        }
    }
}

@Composable
private fun StatusText(status: FirmwareStatus, connected: Boolean) {
    when (status) {
        is FirmwareStatus.Busy -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(status.progress.phase, style = MaterialTheme.typography.bodyLarge)
            val fraction = status.progress.fraction
            if (fraction != null) {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        is FirmwareStatus.Done -> Text(status.message, color = MaterialTheme.colorScheme.primary)
        is FirmwareStatus.Failed -> Text(status.message, color = MaterialTheme.colorScheme.error)
        is FirmwareStatus.Report -> Section("Version") { InfoTable(status.items) }
        FirmwareStatus.Idle -> if (!connected) {
            Text("Connect the Nowind interface to the phone first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
