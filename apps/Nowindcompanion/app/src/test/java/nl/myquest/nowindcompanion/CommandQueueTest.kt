package nl.myquest.nowindcompanion

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeoutException

class CommandQueueTest {

    @Test(expected = TimeoutException::class)
    fun an_incomplete_command_times_out() {
        val queue = CommandQueue()
        queue.add(byteArrayOf(1, 2, 3))
        runBlocking { queue.waitForBytes(9) }
    }

    @Test
    fun read_until_a_terminator() {
        val queue = CommandQueue()
        queue.add("GAME.DSK:rest".toByteArray())
        val name = runBlocking { queue.readUntil(setOf(0, ':'.code), 40) }
        assertEquals("GAME.DSK:", name.map { it.toChar() }.joinToString(""))
        assertEquals(4, queue.size())
    }
}
