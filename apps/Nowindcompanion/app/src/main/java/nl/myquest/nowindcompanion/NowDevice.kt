package nl.myquest.nowindcompanion

import java.io.InputStream
import java.io.OutputStream

// where the files of the NOW: device live, see StorageDeviceFiles
interface DeviceFiles {
    // null when the file does not exist
    fun openRead(name: String): InputStream?

    // null when the file can not be created
    fun openWrite(name: String, append: Boolean): OutputStream?
}

private const val FILE_BUFFER_SIZE = 256
private const val END_OF_FILE = 0x1A

// the NOW: device of Disk BASIC (OPEN "NOW:FILE.TXT" FOR INPUT AS #1), modeled after Device.cc of the C++ host.
// The MSX keeps a 256 byte buffer in its file control block (FCB) and asks for the next 256 bytes when it is empty.
// An open file is known by the address of its FCB.
class NowDevice(private val files: () -> DeviceFiles?) {

    private class OpenFile(val input: InputStream?, val output: OutputStream?)

    private val openFiles = mutableMapOf<Int, OpenFile>()

    // fileModes of the MSX
    private val modeInput = 1
    private val modeOutput = 2
    private val modeRandom = 4
    private val modeAppend = 8

    // in: E = file mode, D = device code, HL = FCB address, followed by the 11 byte file name (8 + 3, space padded)
    fun open(command: Command, fileName: List<Int>, responseQueue: ResponseQueue) {
        val fcb = command.hl
        val openMode = command.getE()
        val name = toFileName(fileName)
        close(fcb)
        Debug.log("NOW: open '$name' mode $openMode, FCB 0x%04X".format(fcb))

        responseQueue.addHeader()
        val deviceFiles = files()
        val file = when (openMode) {
            modeInput -> deviceFiles?.openRead(name)?.let { OpenFile(it, null) }
            modeOutput -> deviceFiles?.openWrite(name, false)?.let { OpenFile(null, it.buffered()) }
            modeAppend -> deviceFiles?.openWrite(name, true)?.let { OpenFile(null, it.buffered()) }
            modeRandom -> {
                responseQueue.add(58)   // sequential I/O only
                return
            }

            else -> {
                responseQueue.add(0xFF)
                return
            }
        }
        if (file == null) {
            responseQueue.add(if (openMode == modeOutput) 56 else 53)   // bad file name, file not found
            return
        }
        openFiles[fcb] = file

        val buffer = if (file.input != null) readBuffer(file.input) else emptyList()
        responseQueue.add(0)                        // no error
        responseQueue.add16(fcb)
        responseQueue.add16(9 + buffer.size)        // number of bytes to copy into the FCB
        responseQueue.add(listOf(openMode, 0, 0, 0, command.getD(), 0, 0, 0, 0))
        responseQueue.add(buffer)
    }

    fun close(fcb: Int) {
        openFiles.remove(fcb)?.let {
            it.input?.close()
            it.output?.close()
        }
    }

    fun closeAll() {
        openFiles.keys.toList().forEach { close(it) }
    }

    // in: C = character, HL = FCB address
    fun write(command: Command) {
        openFiles[command.hl]?.output?.write(command.getC())
    }

    // in: HL = FCB address; no reply for an unknown file, the MSX times out with 'device I/O error'
    fun read(command: Command, responseQueue: ResponseQueue) {
        val input = openFiles[command.hl]?.input ?: return
        val buffer = readBuffer(input)
        responseQueue.addHeader()
        responseQueue.add(0)    // dummy
        responseQueue.add16(command.hl + 9)
        responseQueue.add16(buffer.size)
        responseQueue.add(buffer)
    }

    // the next (at most) 256 bytes; an end of file marker follows when the file ends within them
    private fun readBuffer(input: InputStream): List<Int> {
        val data = ByteArray(FILE_BUFFER_SIZE)
        var length = 0
        while (length < FILE_BUFFER_SIZE) {
            val count = input.read(data, length, FILE_BUFFER_SIZE - length)
            if (count < 0) break
            length += count
        }
        val result = data.take(length).map { it.toInt() and 0xFF }
        return if (length < FILE_BUFFER_SIZE) result + END_OF_FILE else result
    }
}

// 'FILE    TXT' -> 'FILE.TXT'
fun toFileName(fileName: List<Int>): String {
    fun part(from: Int, to: Int) = fileName.subList(from, minOf(to, fileName.size))
        .map { it.toChar() }.joinToString("").substringBefore(' ').uppercase()
    val name = part(0, 8)
    val extension = part(8, 11)
    return if (extension.isEmpty()) name else "$name.$extension"
}
