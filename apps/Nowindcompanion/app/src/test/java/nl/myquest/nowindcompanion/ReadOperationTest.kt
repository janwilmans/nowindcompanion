package nl.myquest.nowindcompanion

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.random.Random

class ReadOperationTest {

    private fun data(size: Int) = Random(size).nextBytes(size)

    private fun read(msx: FakeMsx, address: Int, data: ByteArray) {
        val responseQueue = ResponseQueue()
        val commandQueue = CommandQueue()
        msx.run(responseQueue, commandQueue) {
            ReadOperation(address, data.map { it.toInt() and 0xFF }).execute(responseQueue, commandQueue)
        }
    }

    private fun FakeMsx.at(address: Int, size: Int) = memory.copyOfRange(address, address + size)

    @Test
    fun read_into_page_2_3() {
        val msx = FakeMsx()
        val data = data(9 * 512)
        read(msx, 0xC000, data)
        assertArrayEquals(data, msx.at(0xC000, data.size))
        assertEquals(BlockRead.EXIT.value, msx.frames.last().action)
        assertTrue(msx.frames.none { it.action == BlockRead.EXIT_MORE_DATA_AHEAD.value })
    }

    // a failed block is resent to its own address, not to the start of the batch
    @Test
    fun failed_blocks_are_resent_to_their_own_address() {
        val msx = FakeMsx()
        msx.failAcks += listOf(0, 2)   // the first block sent is the highest one
        val data = data(512)
        read(msx, 0xC000, data)
        assertArrayEquals(data, msx.at(0xC000, data.size))
        val resends = msx.frames.filter { it.action == BlockRead.FASTTRANSFER.value && it.size == 128 }
        assertEquals(listOf(0xC180, 0xC080), resends.map { it.address })
    }

    @Test
    fun a_read_across_8000_switches_to_page_2_3_once() {
        val msx = FakeMsx()
        val data = data(16 * 512)
        read(msx, 0x7000, data)
        assertArrayEquals(data, msx.at(0x7000, data.size))
        val switch = msx.frames.indexOfFirst { it.action == BlockRead.EXIT_MORE_DATA_AHEAD.value }
        assertEquals(1, msx.frames.count { it.action == BlockRead.EXIT_MORE_DATA_AHEAD.value })
        assertTrue(msx.frames.take(switch).all { it.address + it.size <= 0x8000 })
        assertTrue(msx.frames.drop(switch + 1).dropLast(1).all { it.address >= 0x8000 })
    }

    // the block count is one byte: 256 blocks are sent as 0, more need another batch
    @Test
    fun more_than_256_blocks() {
        val msx = FakeMsx()
        val data = data(100 * 512)
        read(msx, 0x0000, data)
        assertArrayEquals(data, msx.at(0x0000, data.size))
        assertTrue(msx.frames.all { it.size <= 256 * 128 })
        assertTrue(msx.frames.any { it.size == 256 * 128 })
    }

    @Test
    fun a_remainder_is_sent_slow() {
        val msx = FakeMsx()
        val data = data(128 + 37)
        read(msx, 0x9000, data)
        assertArrayEquals(data, msx.at(0x9000, data.size))
        assertEquals(Frame(BlockRead.SLOWTRANSFER.value, 0x9080, 37), msx.frames.first { it.action == BlockRead.SLOWTRANSFER.value })
    }

    @Test(expected = IOException::class)
    fun a_block_that_keeps_failing_aborts_the_transfer() {
        val msx = FakeMsx()
        msx.failAllAcks = true
        read(msx, 0xC000, data(512))
    }

    private fun Frame(action: Int, address: Int, size: Int) = FakeMsx.Frame(action, address, size)
}
