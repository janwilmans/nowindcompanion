package nl.myquest.nowindcompanion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection

data class DownloadProgress(val request: DownloadRequest, val bytes: Long, val total: Long) {
    // null when the server did not report a size
    val fraction: Float?
        get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
}

// downloaded disk images in app storage, the download queue worker, and hosting a disk on the interface
class DiskLibrary(
    private val dir: File,
    private val scope: CoroutineScope,
    private val client: FTDIClient,
    private val state: HostState,
    private val settings: SettingsRepository,
) {
    private val lock = Any()
    private val queue = DownloadQueue()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var cancelUrl: String? = null

    private val _pending = MutableStateFlow<List<DownloadRequest>>(emptyList())
    val pending: StateFlow<List<DownloadRequest>> = _pending

    private val _progress = MutableStateFlow<DownloadProgress?>(null)
    val progress: StateFlow<DownloadProgress?> = _progress

    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files

    // short user facing notifications, shown as snackbar
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events

    init {
        dir.mkdirs()
        refreshFiles()
        scope.launch(Dispatchers.IO) { worker() }
    }

    fun fileFor(entry: RemoteEntry): File = File(dir, entry.fileName)

    // an empty file is what is left when the device lost power right after a download, fetch it again
    fun isDownloaded(entry: RemoteEntry): Boolean = fileFor(entry).length() > 0

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
        val file = fileFor(entry)
        if (file.length() > 0) {
            host(file)
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

    fun host(file: File) {
        scope.launch(Dispatchers.IO) {
            try {
                val image = readDiskImage(file)
                client.insertDisk(image)
                state.hostedDisk.value = file.name
                settings.setHostedDisk(file.name)
                state.write("Hosting ${file.name} (${image.size} bytes)")
                _events.tryEmit("Hosting ${file.name}")
            } catch (e: Exception) {
                Debug.error("host ${file.name} failed: $e")
                _events.tryEmit("Could not load ${file.name}: ${e.message}")
            }
        }
    }

    fun delete(file: File) {
        file.delete()
        refreshFiles()
    }

    private fun refreshFiles() {
        _files.value = dir.listFiles { f -> f.isFile && f.length() > 0 && !f.name.endsWith(".part") }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
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
    private fun download(request: DownloadRequest): File {
        val entry = request.entry
        val target = fileFor(entry)
        if (target.length() > 0) return target
        val part = File(dir, target.name + ".part")
        state.write("Download: ${entry.url}")
        val connection = openHttpConnection(entry.url)
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
                FileOutputStream(part).use { output ->
                    copyWithProgress(request, input, output, total)
                    output.fd.sync()
                }
            }
            val file = if (disks > 1) splitDiskSet(entry, part, disks) else {
                if (!part.renameTo(target)) throw IOException("could not save ${target.name}")
                target
            }
            state.write("Done.")
            return file
        } finally {
            connection.disconnect()
            part.delete()
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
    private fun splitDiskSet(entry: RemoteEntry, part: File, disks: Int): File {
        val data = part.readBytes()
        if (data.size % disks != 0) throw IOException("${entry.name}: ${data.size} bytes can not be split in $disks disks")
        val diskSize = data.size / disks
        val files = diskSetFileNames(entry.name, disks).mapIndexed { index, name ->
            File(dir, name).apply {
                FileOutputStream(this).use { output ->
                    output.write(data, index * diskSize, diskSize)
                    output.fd.sync()
                }
            }
        }
        state.write("Saved $disks disks of ${entry.name}")
        return files.first()
    }
}
