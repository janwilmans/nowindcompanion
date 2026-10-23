package nl.myquest.nowindcompanion

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// a firmware file picked on the phone, kept for as long as the app runs
class LocalFirmware(val name: String, val data: ByteArray)

sealed interface FirmwareStatus {
    data object Idle : FirmwareStatus
    data class Busy(val progress: FlashProgress) : FirmwareStatus
    data class Done(val message: String) : FirmwareStatus
    data class Report(val items: List<Pair<String, String>>) : FirmwareStatus
    data class Failed(val message: String) : FirmwareStatus
}

// verifies and updates the firmware of the interface; lives as long as the app, so leaving the screen
// does not stop an update halfway
class FirmwareUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: FTDIClient,
    private val settings: SettingsRepository,
    private val storage: StorageFolder,
) {
    private val _localFile = MutableStateFlow<LocalFirmware?>(null)
    val localFile: StateFlow<LocalFirmware?> = _localFile

    // false: the firmware is downloaded from the url in the settings
    private val _useLocalFile = MutableStateFlow(false)
    val useLocalFile: StateFlow<Boolean> = _useLocalFile

    private val _status = MutableStateFlow<FirmwareStatus>(FirmwareStatus.Idle)
    val status: StateFlow<FirmwareStatus> = _status

    private val busy: Boolean
        get() = _status.value is FirmwareStatus.Busy

    fun useLocalFile(use: Boolean) {
        if (busy) return
        _useLocalFile.value = use && _localFile.value != null
        _status.value = FirmwareStatus.Idle
    }

    fun pickLocalFile(uri: Uri) {
        if (busy) return
        scope.launch(Dispatchers.IO) {
            try {
                val name = DocumentFile.fromSingleUri(context, uri)?.name ?: "firmware.rom"
                val data = context.contentResolver.openInputStream(uri)?.use { readLimited(it.buffered()) }
                    ?: throw IOException("could not open $name")
                checkFirmware(data)
                _localFile.value = LocalFirmware(name, data)
                _useLocalFile.value = true
                _status.value = FirmwareStatus.Idle
            } catch (e: Exception) {
                _status.value = FirmwareStatus.Failed("Could not use this file: ${e.message}")
            }
        }
    }

    fun verify() = run("Verify") {
        val rom = loadFirmware()
        withFlashWriter { writer ->
            when (val offset = writer.verify(rom)) {
                null -> "The interface already has this firmware."
                else -> "The interface has different firmware (the first difference is at 0x%06X).".format(offset)
            }
        }
    }

    fun update() = run("Update") {
        val rom = loadFirmware()
        val erase = settings.settings.first().firmwareErase
        withFlashWriter { writer ->
            writer.update(rom, erase)
            "Firmware updated and verified. Switch the MSX off and on to start it."
        }
    }

    // saved as firmware/nowind-YYMMDD-HHmmss.rom in the storage folder, to restore later as a file on this phone
    fun backup() = run("Backup") {
        val root = storage.root.value ?: throw IOException("no access to the storage folder, choose it first")
        val data = withFlashWriter { it.read() }
        val name = "nowind-${SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())}.rom"
        val file = root.folder("firmware").replaceFile(name)
        storage.write(file) { it.write(data) }
        "Saved the firmware in Download/$APP_FOLDER/firmware/$name."
    }

    // everything the interface tells about itself; changes nothing
    fun checkVersion() {
        if (busy) return
        _status.value = FirmwareStatus.Busy(FlashProgress("Starting", null))
        scope.launch(Dispatchers.IO) {
            try {
                _status.value = FirmwareStatus.Report(client.withFlashLink { link -> versionReport(link) })
            } catch (e: Exception) {
                Debug.error("firmware version check failed: $e")
                _status.value = FirmwareStatus.Failed("Check version failed: ${e.message}")
            }
        }
    }

    private fun versionReport(link: FlashLink): List<Pair<String, String>> {
        val writer = FlashWriter(link) { _status.value = FirmwareStatus.Busy(it) }
        val report = mutableListOf<Pair<String, String>>()
        val chip = writer.identify()
        if (chip == null) {
            report += "Interface" to "unknown, the flash writer does not answer the id command (older firmware, probably V1)"
        } else {
            report += "Interface" to chip.interfaceVersion
            report += "Flash rom" to "${chip.name}, ${chip.size}"
            report += "Flash rom id" to chip.ids
        }
        val versions = writer.versions()
        report += "Firmware" to (versions.firmware ?: "no version found")
        report += "Flash writer" to (versions.flashWriter ?: "no version found")
        report += link.usbInfo()
        return report
    }

    private suspend fun <T> withFlashWriter(work: (FlashWriter) -> T): T =
        client.withFlashLink { link -> work(FlashWriter(link) { _status.value = FirmwareStatus.Busy(it) }) }

    private fun run(action: String, work: suspend () -> String) {
        if (busy) return
        _status.value = FirmwareStatus.Busy(FlashProgress("Starting", null))
        scope.launch(Dispatchers.IO) {
            try {
                _status.value = FirmwareStatus.Done(work())
            } catch (e: Exception) {
                Debug.error("firmware $action failed: $e")
                _status.value = FirmwareStatus.Failed("$action failed: ${e.message}")
            }
        }
    }

    private suspend fun loadFirmware(): ByteArray {
        _localFile.value?.takeIf { _useLocalFile.value }?.let { return it.data }
        val url = expandFirmwareName(settings.settings.first().firmwareSource)
        _status.value = FirmwareStatus.Busy(FlashProgress("Downloading $url", null))
        val connection = openHttpConnection(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode} for $url")
            return connection.inputStream.buffered().use { readLimited(it) }
        } finally {
            connection.disconnect()
        }
    }

    // a wrong url could point at something huge, stop reading once it can not be firmware
    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            output.write(buffer, 0, count)
            if (output.size() > MAX_FIRMWARE_SIZE) throw IOException("the file is larger than the 4 MB flash rom")
        }
    }
}
