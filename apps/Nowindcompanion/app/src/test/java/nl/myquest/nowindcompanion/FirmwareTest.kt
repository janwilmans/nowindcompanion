package nl.myquest.nowindcompanion

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

// answers like msxsrc/flashWriter.asm; expects one complete command per write, like FlashWriter sends them
private class FakeFlashWriter(size: Int = 512 * 1024) : FlashLink {
    val flash = ByteArray(size) { 0x5A }
    private val output = ArrayDeque<Byte>()
    var erases = 0
    var sectorErases = 0

    override fun write(data: ByteArray) {
        val bytes = data.map { it.toInt() and 0xFF }
        val start = bytes.windowed(2).indexOf(listOf(0xBB, 0x55))
        require(start >= 0) { "no command header" }
        val args = bytes.drop(start + 2)
        when (args[0]) {
            0xA4 -> {
                flash.fill(0xFF.toByte())
                erases++
                ack(0)
            }
            0xA5 -> {
                flash.fill(0xFF.toByte(), args[1] * 65536, (args[1] + 1) * 65536)
                sectorErases++
                ack(0)
            }
            0xA3 -> {
                val offset = bank(args) + (args[1] or (args[2] shl 8))
                // flash programming can only clear bits
                for (i in 0 until 128) flash[offset + i] = (flash[offset + i].toInt() and args[4 + i]).toByte()
                ack(2)
            }
            0xA2 -> {
                val offset = bank(args) + (args[1] or (args[2] shl 8)) - 0x4000
                for (i in 0 until 128) output.addLast(flash[offset + i])
                ack(3)
            }
            else -> error("unknown command ${args[0]}")
        }
    }

    private fun bank(args: List<Int>) = args[3] * 16 * 1024

    private fun ack(value: Int) {
        listOf(0xAA, 0x55, value).forEach { output.addLast(it.toByte()) }
    }

    override fun read(count: Int, timeoutMs: Long): ByteArray {
        if (output.size < count) throw IOException("timeout")
        return ByteArray(count) { output.removeFirst() }
    }
}

// a link without an MSX on the other end
private object Silent : FlashLink {
    override fun write(data: ByteArray) {}
    override fun read(count: Int, timeoutMs: Long): ByteArray = throw IOException("timeout")
}

class FirmwareTest {
    private fun rom(size: Int) = ByteArray(size) { (it * 7 + it / 251).toByte() }.apply {
        this[0] = 'A'.code.toByte()
        this[1] = 'B'.code.toByte()
    }

    @Test
    fun update_writes_and_verifies_every_bank() {
        val msx = FakeFlashWriter()
        val firmware = rom(5 * 16 * 1024 + 300)
        FlashWriter(msx).update(firmware)
        assertEquals(1, msx.erases)
        assertArrayEquals(firmware, msx.flash.copyOf(firmware.size))
        assertNull(FlashWriter(msx).verify(firmware))
    }

    @Test
    fun sector_erase_only_erases_what_is_written() {
        val msx = FakeFlashWriter()
        val firmware = rom(64 * 1024 + 1)
        FlashWriter(msx).update(firmware, EraseMethod.Sector)
        assertEquals(0, msx.erases)
        assertEquals(2, msx.sectorErases)
        assertArrayEquals(firmware, msx.flash.copyOf(firmware.size))
        assertEquals(0x5A.toByte(), msx.flash[2 * 65536])     // the third sector is untouched
    }

    @Test
    fun verify_finds_the_first_difference() {
        val msx = FakeFlashWriter()
        val firmware = rom(64 * 1024)
        firmware.copyInto(msx.flash)
        msx.flash[40000] = (msx.flash[40000] + 1).toByte()
        assertEquals(40000, FlashWriter(msx).verify(firmware))
        assertEquals(0, msx.erases)
    }

    @Test
    fun a_backup_reads_the_whole_firmware_and_restores() {
        val msx = FakeFlashWriter()
        val firmware = rom(FIRMWARE_SIZE)
        firmware.copyInto(msx.flash)
        val backup = FlashWriter(msx).read()
        assertArrayEquals(firmware, backup)
        msx.flash.fill(0)
        FlashWriter(msx).update(backup)
        assertNull(FlashWriter(msx).verify(firmware))
    }

    @Test(expected = NoFlashWriterException::class)
    fun no_answer_means_no_flash_writer() {
        FlashWriter(Silent).update(rom(1024))
    }

    @Test(expected = IOException::class)
    fun a_file_without_rom_header_is_refused() {
        checkFirmware(ByteArray(1024))
    }

    @Test
    fun firmware_is_padded_to_whole_blocks() {
        val padded = checkFirmware(rom(130))
        assertEquals(256, padded.size)
        assertEquals(0xFF.toByte(), padded[255])
    }

    @Test
    fun a_name_without_dots_is_a_firmware_site() {
        assertEquals(DEFAULT_FIRMWARE_URL, expandFirmwareName(" nullptr "))
        assertEquals("https://example.com/my.rom", expandFirmwareName("example.com/my.rom"))
        assertEquals("http://192.168.1.2/nowind.rom", expandFirmwareName("http://192.168.1.2/nowind.rom"))
        assertEquals(DEFAULT_FIRMWARE_URL, expandFirmwareName(DEFAULT_FIRMWARE_URL))
    }
}
