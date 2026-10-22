package nl.myquest.nowindcompanion

import java.util.LinkedList

const val TWOBANKLIMIT = 0x8000

// DSKIO read, modeled after BlockRead.cc of the C++ host.
//
// the transfer is split into batches:
//  - a batch never crosses address $8000. The client software needs a different mode to transfer to
//    page 0/1 ($xxxx-$7FFF) than to page 2/3 ($8000-$xxxx): the MSX uses blockRead for the former and
//    blockRead23 for the latter (see 'blockRead:' in common.asm). The switch can be done only once,
//    mid-way through the transfer, and the MSX is told to do so with BlockRead.EXIT_MORE_DATA_AHEAD.
//    So a transfer across $8000 becomes two operations: first the part in page 0/1, then the part
//    in page 2/3, each including all of its own retries.
//  - a fast batch holds at most 256 blocks of 128 bytes (HARDCODED_READ_DATABLOCK_SIZE, the block count
//    is one byte). A multiple of 128 bytes is sent as a so-called 'Fast-Transfer': it uses loop-unrolling
//    and a stack-pointer trick to check only every 128 bytes whether the transfer is done.
//  - less than 128 bytes remaining is sent as one slow block. A 'Slow-Transfer' is a regular LDIR loop,
//    so it can stop after any byte.
class ReadOperation(private val startAddress: Int, private val data: List<Int>) {

    private fun createFastBlocks(address: Int, offset: Int, blocks: Int): LinkedList<DataBlock> {
        val queue = LinkedList<DataBlock>()
        val blockSize = HARDCODED_READ_DATABLOCK_SIZE
        // the first block sent is the one with the highest address, its data is reversed
        for (i in blocks - 1 downTo 0) {
            val blockOffset = offset + i * blockSize
            val blockData = data.subList(blockOffset, blockOffset + blockSize).reversed()
            queue.add(DataBlock(blockData, address + i * blockSize + blockSize, true))
        }
        return queue
    }

    private fun sendBlock(block: DataBlock, responseQueue: ResponseQueue) {
        responseQueue.addBlock(block)
    }

    private suspend fun verify(block: DataBlock, commandQueue: CommandQueue): Boolean {
        commandQueue.restartTimeout()
        val next = commandQueue.next()
        val result = (next == block.marker)
        if (!result) {
            println("verify for head marker ${block.marker} failed, tail marker received: ${next}")
        }
        return result
    }

    private fun resend(block: DataBlock, responseQueue: ResponseQueue) {
        responseQueue.addHeader()
        if (block.fast) {
            responseQueue.add(BlockRead.FASTTRANSFER.value)
            responseQueue.add16(block.transferAddress)
            responseQueue.add(1)
        } else {
            responseQueue.add(BlockRead.SLOWTRANSFER.value)
            responseQueue.add16(block.transferAddress)
            responseQueue.add16(block.data.size)
        }
        sendBlock(block, responseQueue)
    }

    suspend fun execute(responseQueue: ResponseQueue, commandQueue: CommandQueue) {
        val total = data.size
        var processed = 0
        var transferingToPage01 = startAddress < TWOBANKLIMIT
        var blocksSent = 0
        var retransmissions = 0

        while (processed < total) {
            val address = startAddress + processed
            if (transferingToPage01 && address >= TWOBANKLIMIT) {
                // switch to page 2/3 transfers
                responseQueue.addHeader()
                responseQueue.add(BlockRead.EXIT_MORE_DATA_AHEAD.value)
                transferingToPage01 = false
            }

            val remaining = total - processed
            val size = if (address < TWOBANKLIMIT) minOf(remaining, TWOBANKLIMIT - address) else remaining

            val queue: LinkedList<DataBlock>
            responseQueue.addHeader()
            if (size < HARDCODED_READ_DATABLOCK_SIZE) {
                val block = DataBlock(data.subList(processed, processed + size), address, false)
                queue = LinkedList<DataBlock>()
                queue.add(block)
                responseQueue.add(BlockRead.SLOWTRANSFER.value)
                responseQueue.add16(address)
                responseQueue.add16(size)
                processed += size
            } else {
                val blocks = minOf(size / HARDCODED_READ_DATABLOCK_SIZE, 256)
                queue = createFastBlocks(address, processed, blocks)
                responseQueue.add(BlockRead.FASTTRANSFER.value)
                responseQueue.add16(address + blocks * HARDCODED_READ_DATABLOCK_SIZE)  // end address, the MSX writes back to front
                responseQueue.add(blocks and 0xFF)                                      // 256 is sent as 0
                processed += blocks * HARDCODED_READ_DATABLOCK_SIZE
            }
            for (block in queue) {
                sendBlock(block, responseQueue)
            }
            blocksSent += queue.size

            // the MSX acknowledges in order; a failed block is resent immediately to its own address
            // and moved to the back of the queue
            while (queue.isNotEmpty()) {
                val block = queue.first
                if (verify(block, commandQueue)) {
                    queue.removeFirst()
                } else {
                    retransmissions++
                    resend(block, responseQueue)
                    queue.removeFirst()
                    queue.addLast(block)
                }
            }
        }
        if (retransmissions > 0) {
            println("ReadOperation done, ${blocksSent} blocks with ${retransmissions} retransmission(s).")
        }
        responseQueue.addHeader()
        responseQueue.add(BlockRead.EXIT.value)
    }
}
