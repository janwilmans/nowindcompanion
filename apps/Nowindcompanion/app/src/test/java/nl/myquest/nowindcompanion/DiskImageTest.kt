package nl.myquest.nowindcompanion

import org.junit.Assert.assertArrayEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DiskImageTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val disk = ByteArray(720 * 1024) { (it % 251).toByte() }

    private fun zip(vararg entries: Pair<String, ByteArray>): File {
        val file = temp.newFile("test.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return file
    }

    @Test
    fun reads_plain_dsk() {
        val file = temp.newFile("game.dsk").apply { writeBytes(disk) }
        assertArrayEquals(disk, readDiskImage(file))
    }

    @Test
    fun prefers_the_dsk_entry_in_a_zip() {
        val file = zip("readme.txt" to ByteArray(600) { 1 }, "GAME.DSK" to disk)
        assertArrayEquals(disk, readDiskImage(file))
    }

    @Test
    fun falls_back_to_the_first_entry() {
        val file = zip("game.img" to disk)
        assertArrayEquals(disk, readDiskImage(file))
    }

    @Test(expected = IOException::class)
    fun rejects_tiny_images() {
        readDiskImage(zip("game.dsk" to ByteArray(10)))
    }
}
