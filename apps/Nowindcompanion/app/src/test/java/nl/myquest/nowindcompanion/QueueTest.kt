package nl.myquest.nowindcompanion

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

import org.junit.Assert.*
import java.util.LinkedList

fun eprintln(message: String) {
    System.err.println(message)
}

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class NowindProtocolTests {

    val sequence: LinkedList<Int> = LinkedList<Int>()

    fun step(step: Int, expectedStep: Int, message: String) {
        sequence.add(step)
        eprintln("$step: $message")
        assertEquals(expectedStep, step)
    }

    @Test
    fun queue_read_write_ordering() {

        runBlocking {
            val commandQueue: CommandQueue = CommandQueue()
            var count: Int = 0

            val combinedJob = launch {
                launch {

                    step(count++, 0, "queue AF05")
                    commandQueue.add(byteArrayOf(0xAF.toByte(), 0x05.toByte()))
                    step(count++, 4, "queue 0102")
                    commandQueue.add(byteArrayOf(0x01.toByte(), 0x02.toByte()))
                    step(count++, 5, "queue 0102")
                    commandQueue.add(byteArrayOf(0x01.toByte(), 0x02.toByte()))
                    step(count++, 6, "queue 0102")
                    commandQueue.add(byteArrayOf(0x01.toByte(), 0x02.toByte()))
                    step(count++, 7, "queue 0102")
                    commandQueue.add(byteArrayOf(0x01.toByte(), 0x02.toByte()))
                    step(count++, 8, "queue 03")
                    commandQueue.add(byteArrayOf(0x03.toByte()))
                    step(count++, 10, "writer done")
                }

                launch {
                    step(count++, 1, "waitFor 0xAF")
                    commandQueue.waitFor(listOf(0xaf))
                    step(count++, 2, "waitFor 0x05")
                    commandQueue.waitFor(listOf(0x05))
                    step(count++, 3, "waitForBytes 9")
                    commandQueue.waitForBytes(9)
                    step(count++, 9, "reader done")
                }
            }

            combinedJob.join()
        }
    }
}
