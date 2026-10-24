package nl.myquest.nowindcompanion

import java.io.IOException

// Firmware v4.3 can not put a ROM into MSX memory by itself, so a ROM is started from a disk: Disk BASIC runs
// AUTOEXEC.BAS, that starts the loader (romloader/romloader.asm), the loader copies GAME.ROM to RAM and calls INIT.

private const val PAGE = 16 * 1024
const val ROM_IMAGE_SIZE = 2 * PAGE                 // pages 1 and 2, $4000-$BFFF
private const val LOADER_ADDRESS = 0xC400

class UnsupportedRomException(message: String) : IOException(message)

private fun hasHeader(rom: ByteArray, offset: Int) =
    rom.size >= offset + 2 && rom[offset] == 'A'.code.toByte() && rom[offset + 1] == 'B'.code.toByte()

// The ROM as it is seen in pages 1 and 2 of a cartridge slot, placed by its INIT address.
// A ROM of up to 16 KB that starts in page 2 only fills page 2: page 1 stays empty, the loader then puts
// the BASIC ROM back in page 1, like on a real MSX (such ROMs call BASIC routines there).
// Otherwise smaller ROMs are mirrored like on a cartridge.
fun romImage(rom: ByteArray): ByteArray {
    if (rom.size > ROM_IMAGE_SIZE) {
        throw UnsupportedRomException("MegaROMs and ROMs over 32 KB can not be started yet")
    }
    if (!hasHeader(rom, 0)) {
        if (rom.size == ROM_IMAGE_SIZE && hasHeader(rom, PAGE)) {
            throw UnsupportedRomException("this ROM is for $0000-$7FFF, that can not be started yet")
        }
        throw UnsupportedRomException("no ROM header found, this is not an MSX ROM")
    }
    val init = (rom[2].toInt() and 0xFF) or (rom[3].toInt() and 0xFF shl 8)
    if (init == 0) throw UnsupportedRomException("BASIC programs in ROM can not be started yet")
    if (init !in 0x4000..0xBFFF) {
        throw UnsupportedRomException("this ROM starts at $%04X, outside \$4000-\$BFFF, that can not be started yet".format(init))
    }
    val image = ByteArray(ROM_IMAGE_SIZE) { 0xFF.toByte() }
    val page2Only = init >= 0x8000 && rom.size <= PAGE
    val start = if (page2Only) PAGE else 0
    val area = ROM_IMAGE_SIZE - start
    if (area % rom.size == 0) {
        for (offset in start until ROM_IMAGE_SIZE step rom.size) rom.copyInto(image, offset)
    } else {
        rom.copyInto(image, start)
    }
    return image
}

// a 720 KB disk that starts the ROM image; the loader is romloader.bin from the assets
fun buildLoaderDisk(image: ByteArray, loader: ByteArray): ByteArray {
    require(image.size == ROM_IMAGE_SIZE) { "the ROM image must be 32 KB" }
    val autoexec = "10 CLEAR 200,&HC400:BLOAD\"LOADER.BIN\",R\r\n".toByteArray(Charsets.US_ASCII) + 0x1A
    val end = LOADER_ADDRESS + loader.size - 1
    val bload = byteArrayOf(
        0xFE.toByte(),
        LOADER_ADDRESS.toByte(), (LOADER_ADDRESS shr 8).toByte(),
        end.toByte(), (end shr 8).toByte(),
        LOADER_ADDRESS.toByte(), (LOADER_ADDRESS shr 8).toByte(),
    ) + loader
    return Fat12Disk().apply {
        add("AUTOEXEC", "BAS", autoexec)
        add("LOADER", "BIN", bload)
        add("GAME", "ROM", image)
    }.bytes
}

// a double sided 720 KB disk (media $F9) like CALL FORMAT makes, written front to back
private class Fat12Disk {
    private val sectorSize = 512
    private val sectorsPerCluster = 2
    private val fatSectors = 3
    private val rootEntries = 112
    private val totalSectors = 1440
    private val rootStart = 1 + 2 * fatSectors
    private val dataStart = rootStart + rootEntries * 32 / sectorSize

    val bytes = ByteArray(totalSectors * sectorSize)
    private var nextCluster = 2
    private var nextEntry = 0

    init {
        bootSector()
        fat(0, 0xFF9)
        fat(1, 0xFFF)
    }

    // boot code RET: there is no MSX-DOS on the disk, so the disk ROM starts Disk BASIC and runs AUTOEXEC.BAS
    private fun bootSector() {
        byteArrayOf(0xEB.toByte(), 0xFE.toByte(), 0x90.toByte()).copyInto(bytes, 0)
        "NWCOMPAN".toByteArray(Charsets.US_ASCII).copyInto(bytes, 3)
        le16(11, sectorSize)
        bytes[13] = sectorsPerCluster.toByte()
        le16(14, 1)                         // reserved sectors: the boot sector
        bytes[16] = 2                       // FAT copies
        le16(17, rootEntries)
        le16(19, totalSectors)
        bytes[21] = 0xF9.toByte()           // media: double sided, 9 sectors per track
        le16(22, fatSectors)
        le16(24, 9)                         // sectors per track
        le16(26, 2)                         // sides
        bytes[0x1E] = 0xC9.toByte()         // RET
        bytes[510] = 0x55
        bytes[511] = 0xAA.toByte()
    }

    fun add(name: String, extension: String, data: ByteArray) {
        val clusterSize = sectorSize * sectorsPerCluster
        val clusters = maxOf(1, (data.size + clusterSize - 1) / clusterSize)
        val first = nextCluster
        if (dataStart + (first - 2 + clusters) * sectorsPerCluster > totalSectors) throw IOException("the disk is full")
        for (i in 0 until clusters) fat(first + i, if (i == clusters - 1) 0xFFF else first + i + 1)
        data.copyInto(bytes, (dataStart + (first - 2) * sectorsPerCluster) * sectorSize)
        nextCluster += clusters

        val entry = rootStart * sectorSize + nextEntry++ * 32
        name.padEnd(8).toByteArray(Charsets.US_ASCII).copyInto(bytes, entry)
        extension.padEnd(3).toByteArray(Charsets.US_ASCII).copyInto(bytes, entry + 8)
        bytes[entry + 11] = 0x20            // archive
        le16(entry + 26, first)
        le16(entry + 28, data.size and 0xFFFF)
        le16(entry + 30, data.size ushr 16)
    }

    // both FAT copies, 12 bits per cluster
    private fun fat(cluster: Int, value: Int) {
        for (copy in 0 until 2) {
            val offset = (1 + copy * fatSectors) * sectorSize + cluster * 3 / 2
            if (cluster % 2 == 0) {
                bytes[offset] = value.toByte()
                bytes[offset + 1] = ((bytes[offset + 1].toInt() and 0xF0) or (value shr 8 and 0x0F)).toByte()
            } else {
                bytes[offset] = ((bytes[offset].toInt() and 0x0F) or (value shl 4 and 0xF0)).toByte()
                bytes[offset + 1] = (value shr 4).toByte()
            }
        }
    }

    private fun le16(offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value shr 8).toByte()
    }
}
