package nl.myquest.nowindcompanion

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// reads a file back from a FAT12 disk the way the MSX disk ROM does: boot sector, root directory, FAT chain
private class Fat12Reader(private val disk: ByteArray) {
    private fun le16(offset: Int) = (disk[offset].toInt() and 0xFF) or (disk[offset + 1].toInt() and 0xFF shl 8)

    val sectorSize = le16(11)
    val sectorsPerCluster = disk[13].toInt() and 0xFF
    val fatStart = le16(14)
    val fats = disk[16].toInt() and 0xFF
    val rootEntries = le16(17)
    val totalSectors = le16(19)
    val media = disk[21].toInt() and 0xFF
    val fatSectors = le16(22)
    private val rootStart = fatStart + fats * fatSectors
    private val dataStart = rootStart + rootEntries * 32 / sectorSize

    fun names(): List<String> = (0 until rootEntries).mapNotNull { i ->
        val entry = rootStart * sectorSize + i * 32
        if (disk[entry].toInt() == 0) null
        else String(disk, entry, 8, Charsets.US_ASCII).trim() + "." + String(disk, entry + 8, 3, Charsets.US_ASCII).trim()
    }

    private fun fat(cluster: Int, copy: Int = 0): Int {
        val offset = (fatStart + copy * fatSectors) * sectorSize + cluster * 3 / 2
        val value = le16(offset)
        return if (cluster % 2 == 0) value and 0xFFF else value shr 4
    }

    fun read(name: String): ByteArray {
        val index = names().indexOf(name)
        require(index >= 0) { "$name not on the disk" }
        val entry = rootStart * sectorSize + index * 32
        val size = le16(entry + 28) or (le16(entry + 30) shl 16)
        val data = java.io.ByteArrayOutputStream()
        var cluster = le16(entry + 26)
        while (cluster < 0xFF8) {
            val start = (dataStart + (cluster - 2) * sectorsPerCluster) * sectorSize
            data.write(disk, start, sectorsPerCluster * sectorSize)
            assertEquals("both FAT copies agree", fat(cluster, 0), fat(cluster, 1))
            cluster = fat(cluster)
        }
        return data.toByteArray().copyOf(size)
    }
}

class RomDiskTest {
    private fun rom(size: Int, init: Int = 0x4010) = ByteArray(size) { (it % 251).toByte() }.apply {
        this[0] = 'A'.code.toByte()
        this[1] = 'B'.code.toByte()
        this[2] = init.toByte()
        this[3] = (init shr 8).toByte()
    }

    @Test
    fun a_32kb_rom_fills_pages_1_and_2() {
        val rom = rom(32 * 1024)
        assertArrayEquals(rom, romImage(rom))
    }

    @Test
    fun smaller_roms_are_mirrored_like_on_a_cartridge() {
        val rom16 = rom(16 * 1024)
        val image16 = romImage(rom16)
        assertArrayEquals(rom16, image16.copyOfRange(0, 16 * 1024))
        assertArrayEquals(rom16, image16.copyOfRange(16 * 1024, 32 * 1024))

        val rom8 = rom(8 * 1024)
        val image8 = romImage(rom8)
        for (copy in 0 until 4) assertArrayEquals(rom8, image8.copyOfRange(copy * 8192, (copy + 1) * 8192))
    }

    @Test
    fun a_rom_that_starts_in_page_2_only_fills_page_2() {
        // like Sweet Acorn: 16 KB, INIT $8010; page 1 must stay free for the BASIC ROM
        val rom = rom(16 * 1024, init = 0x8010)
        val image = romImage(rom)
        assertTrue(image.copyOfRange(0, 16 * 1024).all { it == 0xFF.toByte() })
        assertArrayEquals(rom, image.copyOfRange(16 * 1024, 32 * 1024))

        val rom8 = rom(8 * 1024, init = 0x8010)
        val image8 = romImage(rom8)
        assertArrayEquals(rom8, image8.copyOfRange(16 * 1024, 24 * 1024))
        assertArrayEquals(rom8, image8.copyOfRange(24 * 1024, 32 * 1024))
    }

    @Test(expected = UnsupportedRomException::class)
    fun a_rom_that_starts_in_page_0_is_refused() {
        // like Beamrider: a mirrored cartridge with INIT $0004
        romImage(rom(16 * 1024, init = 0x0004))
    }

    @Test(expected = UnsupportedRomException::class)
    fun a_basic_rom_is_refused() {
        romImage(rom(16 * 1024, init = 0))
    }

    @Test(expected = UnsupportedRomException::class)
    fun megaroms_are_refused() {
        romImage(rom(128 * 1024))
    }

    @Test(expected = UnsupportedRomException::class)
    fun a_48kb_rom_is_refused() {
        romImage(rom(48 * 1024))
    }

    @Test(expected = UnsupportedRomException::class)
    fun a_rom_for_page_0_is_refused() {
        romImage(ByteArray(32 * 1024).apply { this[0x4000] = 'A'.code.toByte(); this[0x4001] = 'B'.code.toByte() })
    }

    @Test(expected = UnsupportedRomException::class)
    fun a_file_without_header_is_refused() {
        romImage(ByteArray(16 * 1024))
    }

    @Test
    fun the_loader_disk_is_a_720kb_disk_basic_can_boot() {
        val image = romImage(rom(16 * 1024))
        val loader = ByteArray(393) { it.toByte() }
        val disk = buildLoaderDisk(image, loader)
        assertEquals(737280, disk.size)

        val fat = Fat12Reader(disk)
        assertEquals(512, fat.sectorSize)
        assertEquals(1440, fat.totalSectors)
        assertEquals(0xF9, fat.media)
        assertEquals(0xF9, disk[fat.fatStart * 512].toInt() and 0xFF)    // the FAT starts with the media byte
        assertEquals(0xC9, disk[0x1E].toInt() and 0xFF)                  // boot code RET: no MSX-DOS, start Disk BASIC
        assertEquals(listOf("AUTOEXEC.BAS", "LOADER.BIN", "GAME.ROM"), fat.names())

        assertEquals("10 CLEAR 200,&HC400:BLOAD\"LOADER.BIN\",R\r\n\u001A", String(fat.read("AUTOEXEC.BAS"), Charsets.US_ASCII))
        val bload = fat.read("LOADER.BIN")
        // BLOAD header: $FE, start, end, execute
        assertArrayEquals(byteArrayOf(0xFE.toByte(), 0x00, 0xC4.toByte(), 0x88.toByte(), 0xC5.toByte(), 0x00, 0xC4.toByte()), bload.copyOf(7))
        assertArrayEquals(loader, bload.copyOfRange(7, bload.size))
        assertArrayEquals(image, fat.read("GAME.ROM"))
    }

    @Test
    fun the_loader_in_the_assets_is_assembled_for_c400() {
        val loader = File("src/main/assets/romloader.bin").readBytes()
        // fits below the disk work area, and has the FCB for GAME.ROM
        assertTrue(loader.size < 1024)
        assertTrue(String(loader, Charsets.US_ASCII).contains("GAME    ROM"))
        // the first instruction calls a routine of the loader itself, so it was assembled for where BLOAD puts it
        assertEquals(0xCD, loader[0].toInt() and 0xFF)
        assertEquals(0xC4, loader[2].toInt() and 0xFF)
    }
}
