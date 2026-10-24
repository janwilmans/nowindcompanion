package nl.myquest.nowindcompanion

import java.io.IOException

// a chunk never exceeds the 240 bytes that fit in the FIFO of the interface
const val WRITE_CHUNK_SIZE = 240
private const val WRITE_SEQUENCE_NUMBER = 0xAA
private const val MAX_CHUNK_RETRIES = 10

enum class BlockWrite(val value: Int) {
    DATA_AHEAD(0),
    ERROR(1),
    MORE_DATA_FOR_PAGE23(0xFE),
    DONE(0xFF)
}

// DSKIO write, modeled after the 'old diskwrite' of the C++ host (doDiskWrite1/doDiskWrite2 in NowindHost.cc),
// which is what blockWrite01/blockWrite23 in common.asm implement:
//  - the host requests a chunk: header, DATA_AHEAD, address, size, sequence number
//  - the MSX sends: sequence number, size bytes, sequence number
//  - a chunk never crosses $8000, the MSX switches from blockWrite01 to blockWrite23 when the host sends
//    MORE_DATA_FOR_PAGE23 (only once, when the data continues at $8000)
//  - a chunk with a wrong sequence number is requested again
//  - at the end the host sends DONE, or ERROR followed by the MSX error code (0 = write protected)
class WriteOperation(private val startAddress: Int, private val size: Int) {

    // returns the received data, or null when the disk is write protected (the MSX was told so)
    suspend fun execute(responseQueue: ResponseQueue, commandQueue: CommandQueue, writeProtected: Boolean): ByteArray? {
        if (writeProtected) {
            responseQueue.addHeader()
            responseQueue.add(BlockWrite.ERROR.value)
            responseQueue.add(0)    // write protected
            return null
        }

        val data = ByteArray(size)
        var transferred = 0
        var retries = 0
        while (transferred < size) {
            val address = startAddress + transferred
            var chunkSize = minOf(size - transferred, WRITE_CHUNK_SIZE)
            if (address < TWOBANKLIMIT && address + chunkSize > TWOBANKLIMIT) {
                chunkSize = TWOBANKLIMIT - address
            }

            responseQueue.addHeader()
            responseQueue.add(BlockWrite.DATA_AHEAD.value)
            responseQueue.add16(address)
            responseQueue.add16(chunkSize)
            responseQueue.add(WRITE_SEQUENCE_NUMBER)

            val received = commandQueue.readBytes(chunkSize + 2)
            if (received.first() != WRITE_SEQUENCE_NUMBER || received.last() != WRITE_SEQUENCE_NUMBER) {
                if (++retries > MAX_CHUNK_RETRIES) {
                    throw IOException("chunk at 0x%04X failed %d times, write aborted".format(address, MAX_CHUNK_RETRIES))
                }
                Debug.log("write chunk at 0x%04X has sequence numbers %02X/%02X, requested again".format(address, received.first(), received.last()))
                responseQueue.clear()
                commandQueue.clear()
                continue
            }
            for (i in 0 until chunkSize) {
                data[transferred + i] = received[i + 1].toByte()
            }
            transferred += chunkSize

            if (startAddress + transferred == TWOBANKLIMIT && transferred < size) {
                responseQueue.addHeader()
                responseQueue.add(BlockWrite.MORE_DATA_FOR_PAGE23.value)
            }
        }
        if (retries > 0) {
            Debug.log("WriteOperation done, $size bytes with $retries retransmission(s).")
        }
        responseQueue.addHeader()
        responseQueue.add(BlockWrite.DONE.value)
        return data
    }
}
