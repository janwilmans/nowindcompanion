package nl.myquest.nowindcompanion

import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

private const val MIN_DISK_SIZE = 512
private const val MAX_DISK_SIZE = 32 * 1024 * 1024

// reads a .dsk file, or the first .dsk in a .zip (the first file if the zip has no .dsk)
fun readDiskImage(file: File): ByteArray {
    val image = if (file.name.endsWith(".zip", ignoreCase = true)) readFromZip(file) else file.readBytes()
    if (image.size < MIN_DISK_SIZE || image.size > MAX_DISK_SIZE) {
        throw IOException("${file.name}: unexpected disk image size of ${image.size} bytes")
    }
    return image
}

private fun readFromZip(file: File): ByteArray {
    var fallback: ByteArray? = null
    ZipInputStream(file.inputStream().buffered()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                if (entry.name.endsWith(".dsk", ignoreCase = true)) {
                    return zip.readBytes()
                }
                if (fallback == null) {
                    fallback = zip.readBytes()
                }
            }
            entry = zip.nextEntry
        }
    }
    return fallback ?: throw IOException("${file.name}: zip file contains no disk image")
}
