package nl.myquest.nowindcompanion

import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection

data class DownloadProgress(val request: DownloadRequest, val bytes: Long, val total: Long) {
    // null when the server did not report a size
    val fraction: Float?
        get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
}

// a downloaded disk image, a snapshot so the UI does not query the storage provider while drawing
data class LibraryFile(val name: String, val size: Long, val document: DocumentFile)

// downloaded disk images in Download/nowindcompanion/disks and ROMs in .../roms, the download queue worker,
// and hosting a disk (or the loader disk of a ROM) on the interface
class DiskLibrary(
    private val storage: StorageFolder,
    private val scope: CoroutineScope,
    private val client: FTDIClient,
    private val state: HostState,
    private val settings: SettingsRepository,
    private val romLoader: () -> ByteArray,     // romloader.bin, see RomDisk.kt
) {
    private val lock = Any()
    private val queue = DownloadQueue()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    // the disk contents after a DSKIO write; only the newest matters, see saver()
    private val saves = Channel<Pair<DocumentFile, ByteArray>>(Channel.CONFLATED)

    @Volatile
    private var cancelUrl: String? = null

    private val _pending = MutableStateFlow<List<DownloadRequest>>(emptyList())
    val pending: StateFlow<List<DownloadRequest>> = _pending

    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress

    private val _files = MutableStateFlow<List<LibraryFile>>(emptyList())
    val files: StateFlow<List<LibraryFile>> = _files

    // null until the user granted access to the storage folder
    @Volatile
    private var dir: DocumentFile? = null

    @Volatile
    private var romDir: DocumentFile? = null

    // short user facing notifications, shown as snackbar
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events

    init {
        scope.launch(Dispatchers.IO) { worker() }
        scope.launch(Dispatchers.IO) { saver() }
    }

    // blocking, call from a background thread
    fun setFolder(folder: DocumentFile?, roms: DocumentFile? = null) {
        dir = folder
        romDir = roms
        refreshFiles()
        if (folder != null) wake.trySend(Unit)
    }

    // an empty file is what is left when the device lost power right after a download, fetch it again
    private fun find(name: String): DocumentFile? = dirFor(name)?.findFile(name)?.takeIf { it.isFile && it.length() > 0 }

    private fun dirFor(name: String): DocumentFile? = if (hasRomExtension(name)) romDir else dir

    private fun isDownloaded(entry: RemoteEntry): Boolean = _files.value.any { it.name == entry.fileName }

    fun enqueue(entry: RemoteEntry) {
        if (isDownloaded(entry)) {
            _events.tryEmit("${entry.name} is already downloaded")
            return
        }
        val added = synchronized(lock) { queue.enqueue(entry).also { publishQueue() } }
        if (added) {
            _events.tryEmit("Queued ${entry.name}")
            wake.trySend(Unit)
        }
    }

    fun downloadAndHost(entry: RemoteEntry) {
        _files.value.find { it.name == entry.fileName }?.let {
            host(it.document)
            return
        }
        synchronized(lock) {
            queue.enqueueFirst(entry)
            publishQueue()
        }
        wake.trySend(Unit)
    }

    fun cancel(entry: RemoteEntry) {
        synchronized(lock) {
            queue.remove(entry.url)
            publishQueue()
        }
        if (_progress.value?.request?.entry?.url == entry.url) {
            cancelUrl = entry.url
        }
    }

    fun host(file: LibraryFile) = host(file.document)

    // the disk that was hosted last time, when it is still in the library
    fun hostByName(name: String) {
        scope.launch(Dispatchers.IO) { find(name)?.let { host(it) } }
    }

    // CALL IMAGE("GAME.DSK") on the MSX; MSX names are upper case and may leave out the extension
    fun hostByMsxName(name: String) {
        val candidates = if (name.contains('.')) listOf(name) else listOf(name, "$name.dsk", "$name.zip")
        val file = candidates.firstNotNullOfOrNull { candidate -> _files.value.find { it.name.equals(candidate, ignoreCase = true) } }
        if (file == null) {
            state.write("CHANGEIMAGE: '$name' is not in the library")
            _events.tryEmit("The MSX asked for $name, it is not in the library")
            return
        }
        host(file.document)
    }

    private fun host(file: DocumentFile) {
        scope.launch(Dispatchers.IO) {
            val name = file.name.orEmpty()
            try {
                if (hasRomExtension(name)) {
                    hostRom(file, name)
                    return@launch
                }
                val image = readDiskImage(name) { storage.open(file) }
                // the disk in a zip can not be saved back, so it is write protected
                val zipped = name.endsWith(".zip", ignoreCase = true)
                client.insertDisk(image, if (zipped) null else { data -> saves.trySend(file to data) })
                state.hostedDisk.value = name
                settings.setHostedDisk(name)
                state.write("Hosting $name (${image.size} bytes)")
                _events.tryEmit("Hosting $name")
            } catch (e: Exception) {
                Debug.error("host $name failed: $e")
                _events.tryEmit("Could not load $name: ${e.message}")
            }
        }
    }

    // a ROM is started from a disk that only exists in memory, so it is write protected
    private suspend fun hostRom(file: DocumentFile, name: String) {
        val image = romImage(storage.open(file).use { it.readBytes() })
        client.insertDisk(buildLoaderDisk(image, romLoader()), null)
        state.hostedDisk.value = name
        settings.setHostedDisk(name)
        state.write("Hosting ROM $name with the loader disk")
        _events.tryEmit("Hosting $name, reset the MSX to start it")
    }

    fun delete(file: LibraryFile) {
        scope.launch(Dispatchers.IO) {
            file.document.delete()
            refreshFiles()
        }
    }

    // a DSKIO write of a file comes as many small writes, wait for the burst to end and save the result once
    private suspend fun saver() {
        for ((file, data) in saves) {
            delay(1000)
            val newest = saves.tryReceive().getOrNull()
            if (newest == null || newest.first.uri != file.uri) save(file, data)
            if (newest != null) save(newest.first, newest.second)
        }
    }

    private fun save(file: DocumentFile, data: ByteArray) {
        try {
            storage.write(file) { it.write(data) }
            Debug.log("saved ${file.name} after a DSKIO write")
        } catch (e: Exception) {
            Debug.error("saving ${file.name} failed: $e")
            _events.tryEmit("Could not save ${file.name}: ${e.message}")
        }
    }

    private fun refreshFiles() {
        _files.value = listOfNotNull(dir, romDir)
            .flatMap { it.listFiles().asList() }
            .mapNotNull { f ->
                val name = f.name ?: return@mapNotNull null
                val size = f.length()
                if (f.isFile && size > 0 && !name.endsWith(".part")) LibraryFile(name, size, f) else null
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun publishQueue() {
        _pending.value = queue.pending
    }

    private suspend fun worker() {
        while (true) {
            val request = synchronized(lock) { queue.next().also { publishQueue() } }
            if (request == null) {
                wake.receive()
                continue
            }
            try {
                val file = download(request)
                refreshFiles()
                if (request.hostWhenDone) {
                    host(file)
                } else {
                    _events.tryEmit("Downloaded ${request.entry.name}")
                }
            } catch (e: CancelledDownload) {
                _events.tryEmit("Cancelled ${request.entry.name}")
            } catch (e: Exception) {
                Debug.error("download ${request.entry.url} failed: $e")
                _events.tryEmit("Download of ${request.entry.name} failed: ${e.message}")
            } finally {
                _progress.value = null
                cancelUrl = null
            }
        }
    }

    private class CancelledDownload : Exception()

    // blocking, runs on the worker; writes to a .part file first so a broken download never looks complete.
    // returns the file to host: the downloaded file, or the first disk of a multi disk set
    private fun download(request: DownloadRequest): DocumentFile {
        val entry = request.entry
        val dir = dirFor(entry.fileName) ?: throw IOException("no access to the storage folder, choose it first")
        find(entry.fileName)?.let { return it }
        val part = dir.replaceFile(entry.fileName + ".part")
        state.write("Download: ${entry.url}")
        val connection = openHttpConnection(entry.url)
        var saved = false   // renamed to the final name, part now refers to the downloaded disk
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            var total = connection.contentLength.toLong()
            var disks = 1
            connection.inputStream.buffered().use { input ->
                if (entry.fromNowindApi) {
                    val header = parseNowindApiHeader(readHeaderLine(input))
                    total = header.size ?: -1
                    disks = header.disks
                }
                storage.write(part) { output -> copyWithProgress(request, input, output, total) }
            }
            val file = if (disks > 1) splitDiskSet(dir, entry, part, disks) else {
                dir.findFile(entry.fileName)?.delete()
                if (!part.renameTo(entry.fileName)) throw IOException("could not save ${entry.fileName}")
                saved = true
                part
            }
            state.write("Done.")
            return file
        } finally {
            connection.disconnect()
            if (!saved) part.delete()
        }
    }

    private fun copyWithProgress(request: DownloadRequest, input: InputStream, output: OutputStream, total: Long) {
        var bytes = 0L
        var lastReported = 0L
        _progress.value = DownloadProgress(request, 0, total)
        val buffer = ByteArray(16 * 1024)
        while (true) {
            if (cancelUrl == request.entry.url) throw CancelledDownload()
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            bytes += count
            if (bytes - lastReported >= 32 * 1024) {
                _progress.value = DownloadProgress(request, bytes, total)
                lastReported = bytes
            }
        }
    }

    // the metadata line in front of a Nowind API download, see parseNowindApiHeader
    private fun readHeaderLine(input: InputStream): String {
        val line = StringBuilder()
        while (line.length < 1024) {
            val c = input.read()
            if (c < 0) throw IOException("unexpected end of download")
            if (c == '\n'.code) return line.toString()
            line.append(c.toChar())
        }
        throw IOException("no Nowind API download")
    }

    // all disks of a set come in one download, store them as separate disk images
    private fun splitDiskSet(dir: DocumentFile, entry: RemoteEntry, part: DocumentFile, disks: Int): DocumentFile {
        val data = storage.open(part).use { it.readBytes() }
        if (data.size % disks != 0) throw IOException("${entry.name}: ${data.size} bytes can not be split in $disks disks")
        val diskSize = data.size / disks
        val files = diskSetFileNames(entry.name, disks).mapIndexed { index, name ->
            dir.replaceFile(name).also { file ->
                storage.write(file) { output -> output.write(data, index * diskSize, diskSize) }
            }
        }
        state.write("Saved $disks disks of ${entry.name}")
        return files.first()
    }
}
