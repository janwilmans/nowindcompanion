package nl.myquest.nowindcompanion

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

private const val MIN_DISK_SIZE = 512
private const val MAX_DISK_SIZE = 32 * 1024 * 1024

// files in a zip that are certainly not disk images, a ROM must not be hosted as a disk
private val NOT_A_DISK = listOf(".rom", ".mx1", ".mx2", ".ri", ".cas", ".wav", ".txt", ".nfo", ".diz", ".pdf", ".jpg", ".png", ".gif")

// reads a .dsk file, or the first .dsk in a .zip (the first other file if the zip has no .dsk and it is not a ROM etc.)
fun readDiskImage(file: File): ByteArray = readDiskImage(file.name) { file.inputStream() }

fun readDiskImage(name: String, open: () -> InputStream): ByteArray {
    val image = if (name.endsWith(".zip", ignoreCase = true)) readFromZip(open()) else open().use { it.readBytes() }
    if (image.size < MIN_DISK_SIZE || image.size > MAX_DISK_SIZE) {
        throw IOException("unexpected disk image size of ${image.size} bytes")
    }
    return image
}

private fun readFromZip(input: InputStream): ByteArray {
    var fallback: ByteArray? = null
    ZipInputStream(input.buffered()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                if (entry.name.endsWith(".dsk", ignoreCase = true)) {
                    return zip.readBytes()
                }
                if (fallback == null && NOT_A_DISK.none { entry.name.endsWith(it, ignoreCase = true) }) {
                    fallback = zip.readBytes()
                }
            }
            entry = zip.nextEntry
        }
    }
    return fallback ?: throw IOException("the zip file contains no disk image (a ROM can not be hosted as a disk)")
}
