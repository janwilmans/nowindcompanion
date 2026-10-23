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

// a firmware file picked on the phone, kept for as long as the app runs
class LocalFirmware(val name: String, val data: ByteArray)

sealed interface FirmwareStatus {
    data object Idle : FirmwareStatus
    data class Busy(val progress: FlashProgress) : FirmwareStatus
    data class Done(val message: String) : FirmwareStatus
    data class Failed(val message: String) : FirmwareStatus
}

// verifies and updates the firmware of the interface; lives as long as the app, so leaving the screen
// does not stop an update halfway
class FirmwareUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val client: FTDIClient,
    private val settings: SettingsRepository,
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

    fun verify() = run("Verify") { flasher ->
        when (val offset = flasher.verify()) {
            null -> "The interface already has this firmware."
            else -> "The interface has different firmware (the first difference is at 0x%06X).".format(offset)
        }
    }

    fun update() = run("Update") { flasher ->
        flasher.update()
        "Firmware updated and verified. Switch the MSX off and on to start it."
    }

    private fun run(action: String, work: (FirmwareFlasher) -> String) {
        if (busy) return
        _status.value = FirmwareStatus.Busy(FlashProgress("Loading the firmware", null))
        scope.launch(Dispatchers.IO) {
            try {
                val rom = loadFirmware()
                var message = ""
                client.withFlashLink { link ->
                    message = work(FirmwareFlasher(link, rom) { _status.value = FirmwareStatus.Busy(it) })
                }
                _status.value = FirmwareStatus.Done(message)
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
