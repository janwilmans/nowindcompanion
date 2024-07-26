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


    fun addBlock(dataBlock: DataBlock) {
        //println("schedule block with marker ${dataBlock.marker} of size ${dataBlock.data.size}")
        add(dataBlock.marker)
        add(dataBlock.data)
        add(dataBlock.marker)
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
