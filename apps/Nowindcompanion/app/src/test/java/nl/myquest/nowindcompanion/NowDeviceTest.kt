package nl.myquest.nowindcompanion

import org.junit.Assert.assertEquals
import org.junit.Test

class NowDeviceTest {

    private class MemoryFiles : DeviceFiles {
        val files = mutableMapOf<String, ByteArray>()

        override fun openRead(name: String) = files[name]?.inputStream()

        override fun openWrite(name: String, append: Boolean) = object : java.io.ByteArrayOutputStream() {
            init {
                if (append) files[name]?.let { write(it) }
            }

            override fun close() {
                files[name] = toByteArray()
            }
        }
    }

    private fun name(text: String) = text.padEnd(11).map { it.code }
    private fun fcbCommand(mode: Int = 0, character: Int = 0) = Command(character, 0x0400 + mode, 0x9000, 0, 0, 0x88)
    private fun bytes(queue: ResponseQueue) = queue.GetResponse().map { it.toInt() and 0xFF }

    @Test
    fun read_a_file_in_buffers_of_256_bytes() {
        val files = MemoryFiles()
        files.files["TEST.TXT"] = ByteArray(300) { it.toByte() }
        val device = NowDevice { files }
        val queue = ResponseQueue()

        device.open(fcbCommand(mode = 1), name("TEST    TXT"), queue)
        val open = bytes(queue)
        assertEquals(listOf(0xFF, 0xAF, 0x05, 0, 0x00, 0x90), open.take(6))
        assertEquals(9 + 256, open[6] + 256 * open[7])
        assertEquals(listOf(1, 0, 0, 0, 4, 0, 0, 0, 0), open.subList(8, 17))
        assertEquals(256, open.size - 17)

        device.read(fcbCommand(), queue)
        val read = bytes(queue)
        assertEquals(0x9009, read[4] + 256 * read[5])
        assertEquals(44 + 1, read[6] + 256 * read[7])
        assertEquals(0x1A, read.last())
    }

    @Test
    fun open_a_missing_file() {
        val device = NowDevice { MemoryFiles() }
        val queue = ResponseQueue()
        device.open(fcbCommand(mode = 1), name("NONE"), queue)
        assertEquals(listOf(0xFF, 0xAF, 0x05, 53), bytes(queue))
    }

    @Test
    fun write_and_append() {
        val files = MemoryFiles()
        val device = NowDevice { files }
        val queue = ResponseQueue()
        device.open(fcbCommand(mode = 2), name("OUT     TXT"), queue)
        "AB".forEach { device.write(fcbCommand(character = it.code)) }
        device.close(0x9000)
        device.open(fcbCommand(mode = 8), name("OUT     TXT"), queue)
        device.write(fcbCommand(character = 'C'.code))
        device.closeAll()
        assertEquals("ABC", String(files.files.getValue("OUT.TXT")))
    }

    @Test
    fun file_names() {
        assertEquals("FILE.TXT", toFileName(name("file    txt")))
        assertEquals("README", toFileName(name("README")))
    }
}
