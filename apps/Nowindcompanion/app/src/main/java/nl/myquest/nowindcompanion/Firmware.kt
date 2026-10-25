package nl.myquest.nowindcompanion

import java.io.IOException
import java.security.MessageDigest

const val MAX_FIRMWARE_SIZE = 4 * 1024 * 1024       // the flash rom of the v2 interface, v1 has 512 KB
const val FIRMWARE_SIZE = 512 * 1024                // nowind.rom: 32 banks, all of the v1 flash rom

private const val BLOCK_SIZE = 128                  // the flash writer reads and writes blocks of 128 bytes
private const val BANK_SIZE = 16 * 1024
private const val SECTOR_SIZE = 64 * 1024           // the smallest part of the flash rom that can be erased

// the commands of the flash writer on the MSX, see msxsrc/flashWriter.asm
private const val CMD_VERIFY = 0xA2
private const val CMD_WRITE = 0xA3
private const val CMD_CHIP_ERASE = 0xA4
private const val CMD_SECTOR_ERASE = 0xA5           // not in the flash writer of the v1 interface
private const val CMD_AUTOSELECT = 0xA6             // the flash rom reports its manufacturer and device id

private const val ANSWER_TIMEOUT_MS = 3_000L
private const val ERASE_TIMEOUT_MS = 10 * 60_000L    // a chip erase of the 4 MB flash rom can take minutes
private const val SECTOR_ERASE_TIMEOUT_MS = 30_000L

// a name without dots is short for that site's firmware: 'example' -> https://example.nl/nowind/files/nowind.rom
fun expandFirmwareName(input: String): String {
    val name = input.trim()
    return if ('.' !in name && "://" !in name) "https://$name.nl/nowind/files/nowind.rom" else normalizeSourceUrl(name)
}

// rejects files that are certainly not Nowind firmware, and pads to whole blocks with 0xFF (what erased flash reads as)
fun checkFirmware(rom: ByteArray): ByteArray {
    if (rom.isEmpty()) throw IOException("the firmware file is empty")
    if (rom.size > MAX_FIRMWARE_SIZE) throw IOException("${formatSize(rom.size.toLong())} is too large for the flash rom")
    if (rom.size < 2 || rom[0] != 'A'.code.toByte() || rom[1] != 'B'.code.toByte()) {
        throw IOException("no ROM header found, this is not a Nowind firmware file")
    }
    val padded = rom.copyOf((rom.size + BLOCK_SIZE - 1) / BLOCK_SIZE * BLOCK_SIZE)
    padded.fill(0xFF.toByte(), rom.size)
    return padded
}

// the interface while the MSX runs the flash writer
interface FlashLink {
    fun write(data: ByteArray)

    // exactly count bytes, IOException when they do not arrive within timeoutMs
    fun read(count: Int, timeoutMs: Long): ByteArray

    // what the USB side of the interface tells about itself, as label and value
    fun usbInfo(): List<Pair<String, String>> = emptyList()
}

// the flash rom as it identifies itself, see autoselectMode in flashWriter.asm
data class FlashChip(val manufacturer: Int, val device: Int) {
    private val known = when (manufacturer to device) {
        0x01 to 0xA4 -> Triple("AMD AM29F040", "512 KB", "V1")
        0x20 to 0xAC -> Triple("ST M29F032-D", "4 MB", "V2 (Sunrise)")
        else -> null
    }
    val name: String get() = known?.first ?: "unknown"
    val size: String get() = known?.second ?: "unknown"
    val interfaceVersion: String get() = known?.third ?: "unknown"
    val ids: String get() = "manufacturer 0x%02X, device 0x%02X".format(manufacturer, device)
}

data class FirmwareVersions(val firmware: String?, val flashWriter: String?)

private val FIRMWARE_VERSION = Regex("""Nowind Interface v\d+(\.\d+)*( \[beta])?""")
private val FLASH_WRITER_VERSION = Regex("""Nowind Flash Writer( Mode)? v\d+(\.\d+)*""")

// the banners the firmware prints at boot and in flash writer mode
fun findFirmwareVersions(rom: ByteArray): FirmwareVersions {
    val text = String(rom, Charsets.ISO_8859_1)
    return FirmwareVersions(FIRMWARE_VERSION.find(text)?.value, FLASH_WRITER_VERSION.find(text)?.value)
}

// firstDifference: offset of the first byte that differs from the firmware, null when equal;
// sha256: of the first size bytes of the flash rom as read
data class VerifyResult(val firstDifference: Int?, val sha256: String, val size: Int)

data class FlashProgress(val phase: String, val fraction: Float?)

class NoFlashWriterException : IOException("The MSX does not answer. Is it in Nowind Flash Writer Mode (red screen)?")

// how the flash rom is erased before writing
enum class EraseMethod {
    Chip,       // the whole flash rom, like 'nwhostapp --flash'; works on every interface
    Sector,     // only the 64 KB sectors that are written, like 'nwhostapp --glash'; faster, v2 small form factor only
}

// reads and writes the flash rom of the interface the way nwhostapp does, through the flash writer on the MSX.
// blocking, every step waits for the MSX to answer
class FlashWriter(
    private val link: FlashLink,
    private val onProgress: (FlashProgress) -> Unit = {},
) {
    // the first size bytes of the flash rom
    fun read(size: Int = FIRMWARE_SIZE): ByteArray = read(0, size, "Reading")

    private fun read(offset: Int, size: Int, phase: String): ByteArray {
        val blocks = size / BLOCK_SIZE
        val data = ByteArray(blocks * BLOCK_SIZE)
        for (block in 0 until blocks) {
            readBlock(offset / BLOCK_SIZE + block).copyInto(data, block * BLOCK_SIZE)
            progress(phase, block + 1, blocks)
        }
        return data
    }

    // null when the flash writer does not know the command (it ignores unknown commands), older firmware may not
    fun identify(): FlashChip? {
        readBlock(0)    // NoFlashWriterException when the MSX is not in flash writer mode
        link.write(command(CMD_AUTOSELECT))
        return try {
            waitForAck(ANSWER_TIMEOUT_MS, readLast = false)
            val ids = link.read(2, ANSWER_TIMEOUT_MS)
            FlashChip(ids[0].toInt() and 0xFF, ids[1].toInt() and 0xFF)
        } catch (e: IOException) {
            null
        }
    }

    // the versions are in bank 5 (the Nowind functions); other firmware may have them elsewhere, then all banks are searched
    fun versions(): FirmwareVersions {
        val bank5 = findFirmwareVersions(read(5 * BANK_SIZE, BANK_SIZE, "Reading the firmware version"))
        if (bank5.firmware != null && bank5.flashWriter != null) return bank5
        return findFirmwareVersions(read(0, FIRMWARE_SIZE, "Searching the firmware for its version"))
    }

    // the offset of the first byte in the flash rom that differs from the firmware, null when it holds the firmware
    fun verify(firmware: ByteArray): Int? {
        val rom = checkFirmware(firmware)
        val blocks = rom.size / BLOCK_SIZE
        for (block in 0 until blocks) {
            val offset = block * BLOCK_SIZE
            val data = readBlock(block)
            for (i in 0 until BLOCK_SIZE) {
                if (data[i] != rom[offset + i]) return offset + i
            }
            progress("Verifying", block + 1, blocks)
        }
        return null
    }

    // like verify, but always reads the whole range so the hash covers what the interface holds
    fun verifyWithHash(firmware: ByteArray): VerifyResult {
        val rom = checkFirmware(firmware)
        val blocks = rom.size / BLOCK_SIZE
        val digest = MessageDigest.getInstance("SHA-256")
        var difference: Int? = null
        for (block in 0 until blocks) {
            val offset = block * BLOCK_SIZE
            val data = readBlock(block)
            digest.update(data)
            if (difference == null) {
                for (i in 0 until BLOCK_SIZE) {
                    if (data[i] != rom[offset + i]) {
                        difference = offset + i
                        break
                    }
                }
            }
            progress("Verifying", block + 1, blocks)
        }
        return VerifyResult(difference, digest.digest().joinToString("") { "%02x".format(it) }, rom.size)
    }

    fun update(firmware: ByteArray, erase: EraseMethod = EraseMethod.Chip) {
        val rom = checkFirmware(firmware)
        readBlock(0)    // fails quickly when the MSX is not in flash writer mode, before the long erase
        when (erase) {
            EraseMethod.Chip -> {
                onProgress(FlashProgress("Erasing, this can take a few minutes", null))
                link.write(command(CMD_CHIP_ERASE))
                waitForAck(ERASE_TIMEOUT_MS)
            }
            EraseMethod.Sector -> {
                val sectors = (rom.size + SECTOR_SIZE - 1) / SECTOR_SIZE
                for (sector in 0 until sectors) {
                    onProgress(FlashProgress("Erasing sector ${sector + 1} of $sectors", sector.toFloat() / sectors))
                    link.write(command(CMD_SECTOR_ERASE) + byteArrayOf(sector.toByte()))
                    waitForAck(SECTOR_ERASE_TIMEOUT_MS)
                }
            }
        }
        val blocks = rom.size / BLOCK_SIZE
        for (block in 0 until blocks) {
            val offset = block * BLOCK_SIZE
            // the flash writer maps the bank in page 0 for writing
            link.write(command(CMD_WRITE, offset % BANK_SIZE, offset / BANK_SIZE) + rom.copyOfRange(offset, offset + BLOCK_SIZE))
            waitForAck(ANSWER_TIMEOUT_MS)
            progress("Writing", block + 1, blocks)
        }
        verify(rom)?.let { throw IOException("verify failed at 0x%06X, the firmware was not written correctly".format(it)) }
    }

    private fun readBlock(block: Int): ByteArray {
        val offset = block * BLOCK_SIZE
        // reading is done in page 1
        link.write(command(CMD_VERIFY, 0x4000 + offset % BANK_SIZE, offset / BANK_SIZE))
        val data = try {
            link.read(BLOCK_SIZE, ANSWER_TIMEOUT_MS)
        } catch (e: IOException) {
            if (block == 0) throw NoFlashWriterException()
            throw e
        }
        waitForAck(ANSWER_TIMEOUT_MS)
        return data
    }

    private fun progress(phase: String, done: Int, blocks: Int) {
        if (done == blocks || done % 32 == 0) onProgress(FlashProgress(phase, done.toFloat() / blocks))
    }

    // the dummy byte 0xEE is there because the flash writer polls for 0xBB 0x55 and may miss the first byte
    private fun command(cmd: Int, address: Int? = null, bank: Int = 0): ByteArray {
        val header = byteArrayOf(0xEE.toByte(), 0xBB.toByte(), 0x55, cmd.toByte())
        if (address == null) return header
        return header + byteArrayOf(address.toByte(), (address shr 8).toByte(), bank.toByte())
    }

    // every command ends with 0xAA 0x55 and one more byte (two for autoselect, read by the caller)
    private fun waitForAck(timeoutMs: Long, readLast: Boolean = true) {
        var previous = 0
        while (true) {
            val value = link.read(1, timeoutMs)[0].toInt() and 0xFF
            if (previous == 0xAA && value == 0x55) break
            previous = value
        }
        if (readLast) link.read(1, timeoutMs)
    }
}
