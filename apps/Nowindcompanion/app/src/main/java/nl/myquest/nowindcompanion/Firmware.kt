package nl.myquest.nowindcompanion

import java.io.IOException

const val DEFAULT_FIRMWARE_URL = "https://nullptr.nl/nowind/files/nowind.rom"
const val MAX_FIRMWARE_SIZE = 4 * 1024 * 1024       // the flash rom of the v2 interface, v1 has 512 KB

private const val BLOCK_SIZE = 128                  // the flash writer reads and writes blocks of 128 bytes
private const val BANK_SIZE = 16 * 1024

// the commands of the flash writer on the MSX, see msxsrc/flashWriter.asm
private const val CMD_VERIFY = 0xA2
private const val CMD_WRITE = 0xA3
private const val CMD_CHIP_ERASE = 0xA4

private const val ANSWER_TIMEOUT_MS = 3_000L
private const val ERASE_TIMEOUT_MS = 10 * 60_000L    // a chip erase of the 4 MB flash rom can take minutes

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
}

data class FlashProgress(val phase: String, val fraction: Float?)

class NoFlashWriterException : IOException("The MSX does not answer. Is it in Nowind Flash Writer Mode (red screen)?")

// writes firmware to the flash rom of the interface the way 'nwhostapp --flash' does: chip erase, write, verify.
// blocking, every step waits for the MSX to answer
class FirmwareFlasher(
    private val link: FlashLink,
    rom: ByteArray,
    private val onProgress: (FlashProgress) -> Unit = {},
) {
    private val rom = checkFirmware(rom)
    private val blocks = this.rom.size / BLOCK_SIZE

    // the offset of the first byte in the flash rom that differs from the firmware, null when it holds the firmware
    fun verify(): Int? {
        for (block in 0 until blocks) {
            val offset = block * BLOCK_SIZE
            val data = readBlock(block)
            for (i in 0 until BLOCK_SIZE) {
                if (data[i] != rom[offset + i]) return offset + i
            }
            progress("Verifying", block + 1)
        }
        return null
    }

    fun update() {
        readBlock(0)    // fails quickly when the MSX is not in flash writer mode, before the long erase
        onProgress(FlashProgress("Erasing, this can take a few minutes", null))
        link.write(command(CMD_CHIP_ERASE))
        waitForAck(ERASE_TIMEOUT_MS)
        for (block in 0 until blocks) {
            val offset = block * BLOCK_SIZE
            // the flash writer maps the bank in page 0 for writing
            link.write(command(CMD_WRITE, offset % BANK_SIZE, offset / BANK_SIZE) + rom.copyOfRange(offset, offset + BLOCK_SIZE))
            waitForAck(ANSWER_TIMEOUT_MS)
            progress("Writing", block + 1)
        }
        verify()?.let { throw IOException("verify failed at 0x%06X, the firmware was not written correctly".format(it)) }
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

    private fun progress(phase: String, done: Int) {
        if (done == blocks || done % 32 == 0) onProgress(FlashProgress(phase, done.toFloat() / blocks))
    }

    // the dummy byte 0xEE is there because the flash writer polls for 0xBB 0x55 and may miss the first byte
    private fun command(cmd: Int, address: Int? = null, bank: Int = 0): ByteArray {
        val header = byteArrayOf(0xEE.toByte(), 0xBB.toByte(), 0x55, cmd.toByte())
        if (address == null) return header
        return header + byteArrayOf(address.toByte(), (address shr 8).toByte(), bank.toByte())
    }

    // every command ends with 0xAA 0x55 and one more byte
    private fun waitForAck(timeoutMs: Long) {
        var previous = 0
        while (true) {
            val value = link.read(1, timeoutMs)[0].toInt() and 0xFF
            if (previous == 0xAA && value == 0x55) break
            previous = value
        }
        link.read(1, timeoutMs)
    }
}
