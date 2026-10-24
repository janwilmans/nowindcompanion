package nl.myquest.nowindcompanion

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WriteOperationTest {

    private fun write(msx: FakeMsx, address: Int, size: Int, writeProtected: Boolean = false): ByteArray? {
        val responseQueue = ResponseQueue()
        val commandQueue = CommandQueue()
        var result: ByteArray? = null
        msx.run(responseQueue, commandQueue, writing = true) {
            result = WriteOperation(address, size).execute(responseQueue, commandQueue, writeProtected)
        }
        return result
    }

    private fun FakeMsx.fill(address: Int, size: Int): ByteArray =
        Random(address).nextBytes(size).also { it.copyInto(memory, address) }

    @Test
    fun write_from_page_2_3() {
        val msx = FakeMsx()
        val data = msx.fill(0xC000, 3 * 512)
        assertArrayEquals(data, write(msx, 0xC000, data.size))
        val chunks = msx.frames.filter { it.action == BlockWrite.DATA_AHEAD.value }
        assertTrue(chunks.all { it.size <= WRITE_CHUNK_SIZE })
        assertEquals(BlockWrite.DONE.value, msx.frames.last().action)
    }

    @Test
    fun a_write_across_8000_tells_the_msx_to_switch() {
        val msx = FakeMsx()
        val data = msx.fill(0x7E00, 4 * 512)
        assertArrayEquals(data, write(msx, 0x7E00, data.size))
        val switch = msx.frames.indexOfFirst { it.action == BlockWrite.MORE_DATA_FOR_PAGE23.value }
        assertEquals(1, msx.frames.count { it.action == BlockWrite.MORE_DATA_FOR_PAGE23.value })
        assertTrue(msx.frames.take(switch).all { it.address + it.size <= 0x8000 })
        assertEquals(0x8000, msx.frames[switch + 1].address)
    }

    @Test
    fun a_write_that_ends_at_8000_does_not_switch() {
        val msx = FakeMsx()
        val data = msx.fill(0x7C00, 2 * 512)
        assertArrayEquals(data, write(msx, 0x7C00, data.size))
        assertTrue(msx.frames.none { it.action == BlockWrite.MORE_DATA_FOR_PAGE23.value })
    }

    @Test
    fun a_chunk_with_a_wrong_sequence_number_is_requested_again() {
        val msx = FakeMsx()
        msx.failChunks += 1
        val data = msx.fill(0xC000, 512)
        assertArrayEquals(data, write(msx, 0xC000, data.size))
        val chunks = msx.frames.filter { it.action == BlockWrite.DATA_AHEAD.value }
        assertEquals(chunks[1], chunks[2])
    }

    @Test
    fun a_write_protected_disk_is_reported() {
        val msx = FakeMsx()
        assertNull(write(msx, 0xC000, 512, writeProtected = true))
        assertEquals(listOf(FakeMsx.Frame(BlockWrite.ERROR.value, size = 0)), msx.frames)
    }
}
