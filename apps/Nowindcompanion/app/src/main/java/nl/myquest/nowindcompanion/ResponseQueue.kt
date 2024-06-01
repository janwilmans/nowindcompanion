package nl.myquest.nowindcompanion

import kotlinx.coroutines.yield
import java.io.IOException
import java.lang.IllegalStateException
import java.util.LinkedList

class ResponseQueue(
    private val queue: LinkedList<Int> = LinkedList<Int>()
) {

    fun clear() {
        queue.clear()
    }

    fun addHeader() {
        // leading 0xff required because the first read can fail (due to a hardware design choice!)
        add(listOf(0xff, 0xaf, 0x05))
    }

    fun add(value: Int) {
        queue.add(value)
    }

    fun add16(value: Int) {
        queue.add(value and 255)
        queue.add((value / 256) and 255)
    }

    // the protocol relies on the block size being smaller then 255 bytes,
    // because that means there is always an unused value in the block.
    private fun GetMarker(data: List<Int>): Int {
        require(data.size < 255)
        var occurring = BooleanArray(256)
        for (i in data) {
            occurring[i] = true
        }
        for (i in occurring.indices) {
            if (occurring[i] == false) {
                return i;
            }
        }
        throw IllegalStateException("GetMarker could not find unused marker")
    }

    fun addBlock(data: List<Int>) {
        val marker = GetMarker(data)
        println("  schedule block with marker $marker of size ${data.size}")
        add(marker)
        add(data)
        add(marker)
    }

    fun addBlocks(data: List<Int>) {
        val blockSize = HARDCODED_READ_DATABLOCK_SIZE
        var startIndex = 0

        // Loop through the data list in chunks of blockSize
        while (startIndex < data.size) {
            // Calculate the end index for the current block, ensuring it doesn't exceed the list size
            val endIndex = minOf(startIndex + blockSize, data.size)

            // Create a sublist for the current block and pass it to addBlock
            val block = data.subList(startIndex, endIndex)
            addBlock(block)

            // Move to the next block
            startIndex = endIndex
        }
    }

    fun add(data: List<Int>) {
        for (value in data) {
            queue.add(value)
        }
    }

    fun size(): Int {
        return queue.size
    }

    fun GetResponse(): ByteArray {
        val data = ByteArray(queue.size)
        for (i in queue.indices) {
            data[i] = queue[i].toByte()
        }
        queue.clear()
        return data
    }
}
