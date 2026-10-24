package nl.myquest.nowindcompanion

import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

// the MSX side of blockRead (common.asm): reads what the host sends and answers like the ROM does
class FakeMsx {
    val memory = ByteArray(0x10000)

    // what the host asked for, in order, to check how a transfer was split
    data class Frame(val action: Int, val address: Int = 0, val size: Int = 0)

    val frames = mutableListOf<Frame>()

    // acknowledges with these numbers (counting from 0) get a wrong marker; the block is also stored corrupted
    val failAcks = mutableSetOf<Int>()
    var failAllAcks = false
    private var acks = 0

    // runs the host operation until it is done, feeding the MSX answers back to it
    fun run(responseQueue: ResponseQueue, commandQueue: CommandQueue, operation: suspend () -> Unit) = runBlocking {
        val job: Job = launch { operation() }
        while (job.isActive) {
            process(responseQueue.GetResponse(), commandQueue)
            yield()
        }
        job.join()
        process(responseQueue.GetResponse(), commandQueue)
    }

    private fun process(response: ByteArray, commandQueue: CommandQueue) {
        val bytes = response.map { it.toInt() and 0xFF }
        var i = 0
        fun next() = bytes[i++]
        fun next16() = next() + 256 * next()
        while (i < bytes.size) {
            // header: FF AF 05
            check(next() == 0xFF && next() == 0xAF && next() == 0x05) { "no header at ${i - 3}" }
            val action = next()
            when (action) {
                BlockRead.FASTTRANSFER.value -> {
                    val endAddress = next16()
                    val blocks = next().let { if (it == 0) 256 else it }
                    frames += Frame(action, endAddress - blocks * 128, blocks * 128)
                    var address = endAddress
                    repeat(blocks) {
                        val marker = next()
                        repeat(128) { memory[--address] = next().toByte() }
                        val tail = next()
                        check(marker == tail)
                        ack(tail, address, 128, commandQueue)
                    }
                }

                BlockRead.SLOWTRANSFER.value -> {
                    val address = next16()
                    val size = next16()
                    frames += Frame(action, address, size)
                    val marker = next()
                    repeat(size) { memory[address + it] = next().toByte() }
                    val tail = next()
                    check(marker == tail)
                    ack(tail, address, size, commandQueue)
                }

                else -> frames += Frame(action)
            }
        }
    }

    private fun ack(marker: Int, address: Int, size: Int, commandQueue: CommandQueue) {
        val fail = failAllAcks || acks in failAcks
        acks++
        if (fail) {
            repeat(size) { memory[address + it] = 0xEE.toByte() }
        }
        commandQueue.add(byteArrayOf((if (fail) marker + 1 else marker).toByte()))
    }
}
