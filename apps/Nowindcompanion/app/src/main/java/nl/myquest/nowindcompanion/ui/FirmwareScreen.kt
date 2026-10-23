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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import nl.myquest.nowindcompanion.ConnectionState
import nl.myquest.nowindcompanion.APP_FOLDER
import nl.myquest.nowindcompanion.DEFAULT_FIRMWARE_URL
import nl.myquest.nowindcompanion.EraseMethod
import nl.myquest.nowindcompanion.FirmwareStatus
import nl.myquest.nowindcompanion.FirmwareUpdater
import nl.myquest.nowindcompanion.expandFirmwareName
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
    var source by rememberSaveable { mutableStateOf(firmwareSource) }
    var confirming by remember { mutableStateOf(false) }
    val busy = status is FirmwareStatus.Busy
    val connected = connection == ConnectionState.Hosting
    val canStart = connected && !busy && (useLocalFile || source.isNotBlank())

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { firmware.pickLocalFile(it) }
    }

    // the url is saved when it is used or when leaving the screen, not on every key press
    val latestSource by rememberUpdatedState(source)
    DisposableEffect(Unit) {
        onDispose { onFirmwareSource(latestSource) }
    }
    val start: (() -> Unit) -> Unit = { action ->
        onFirmwareSource(source)
        action()
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
                    Text("Download", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    OutlinedTextField(
                        value = source,
                        onValueChange = {
                            source = it
                            firmware.useLocalFile(false)
                        },
                        label = { Text("URL") },
                        singleLine = true,
                        enabled = !busy,
                        trailingIcon = {
                            if (source.trim() != DEFAULT_FIRMWARE_URL) {
                                IconButton(onClick = { source = DEFAULT_FIRMWARE_URL }, enabled = !busy) {
                                    Icon(Icons.Rounded.Restore, contentDescription = "Use the default url")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // a short name stands for a longer url, show which one
                    val url = if (source.isBlank()) "" else expandFirmwareName(source)
                    if (url.isNotEmpty() && url != source.trim()) {
                        Text(
                            url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                        )
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { start(firmware::verify) }, enabled = canStart, modifier = Modifier.weight(1f)) {
                    Text("Verify")
                }
                Button(onClick = { confirming = true }, enabled = canStart, modifier = Modifier.weight(1f)) {
                    Text("Update")
                }
            }
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
            Text(
                "Check version reports the interface version, its flash rom, the firmware version and the USB details.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (storageReady) "Saves the firmware of the interface in Download/$APP_FOLDER/firmware as nowind-YYMMDD-HHmmss.rom. " +
                        "To restore it, choose that file as 'File on this phone' and update."
                else "Backups are saved in Download/$APP_FOLDER, choose that storage folder first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
                    start(firmware::update)
                }) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Cancel") }
            }
        )
    }
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
