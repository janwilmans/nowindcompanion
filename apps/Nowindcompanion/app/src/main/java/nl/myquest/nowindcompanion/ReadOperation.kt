package nl.myquest.nowindcompanion

class ReadOperation(val address: Int, data: List<Int>) {
    var dataBlocks: List<DataBlock>

    init {
        dataBlocks = createDataBlocks(address, data)
    }

    // these are different operations needed:
    // right now we create one series of 128 byte blocks, where we do not care about the destination address
    // however:
    // scenario 1) - transferring across address $8000
    // transfer must be split into two operations
    //   - first the part $xxxx-$7FFF (Page0 and Page1), including all retries in those pages
    //   - second the part $8000-$xxxx (Page2 and Page3), including all retries in those pages
    // this is because the client software needs to switch to a different mode to transfer to different pages.
    // this switch can be done only once mid-way through the transfer. see 'blockRead:' in common.asm
    //
    // scenario 2) - transferring not-a-multiple of HARDCODED_READ_DATABLOCK_SIZE (128-bytes)
    // When a multiple of HARDCODED_READ_DATABLOCK_SIZE (128-bytes) length transfer is requested we use a so-called 'Fast-Transfer'.
    // This kind of transfer uses loop-unrolling and a stack-pointer trick to check only every 128 bytes if the transfer is done.
    // For smaller transfers a 'Slow-Transfer' must be used.
    // This kind of transfer is just a regular LDIR loop so it can stop after any byte.

    private fun createDataBlocks(startAddress: Int, data: List<Int>): List<DataBlock> {
        var dataBlocks: MutableList<DataBlock> = mutableListOf()
        val blockSize = HARDCODED_READ_DATABLOCK_SIZE
        val reverseData = data.reversed()
        var address = startAddress
        for (i in data.indices step blockSize) {
            val end = minOf(i + blockSize, data.size)
            val sublist = reverseData.subList(i, end)
            dataBlocks.add(DataBlock(sublist))
            address += blockSize
        }
        return dataBlocks
    }

    private suspend fun verify(block: DataBlock, commandQueue: CommandQueue): Boolean {
        commandQueue.restartTimeout()
        val next = commandQueue.next()
        val result = (next == block.marker);
        if (result == false) {
            println("verify for head marker ${block.marker} failed, tail marker received: ${next}")
        }
        return result
    }

    private suspend fun transferDone(responseQueue: ResponseQueue) {
        responseQueue.addHeader()
        responseQueue.add(BlockRead.EXIT.value)
    }


    public suspend fun execute(responseQueue: ResponseQueue, commandQueue: CommandQueue) {
        // send dataBlocks one-by-one

        var blocks = dataBlocks.size
        var retransmissions = 0
        while (dataBlocks.size > 0) {

            responseQueue.addHeader()
            val size = dataBlocks.size * HARDCODED_READ_DATABLOCK_SIZE
            responseQueue.add(BlockRead.FASTTRANSFER.value)
            responseQueue.add16(address + size)     // point to the end-address, data written back to front by stack-pointer writes
            responseQueue.add(dataBlocks.size)
            for (block in dataBlocks) {
                responseQueue.addBlock(block)
            }

            var retransmit: MutableList<DataBlock> = mutableListOf()
            for (block in dataBlocks) {

                if (verify(block, commandQueue) == false) {
                    // block returned incorrect marker, schedule it for re-transmission
                    retransmit.add(block)
                    retransmissions = retransmissions + 1
                }
            }
            dataBlocks = retransmit
        }
        if (retransmissions > 0) {
            println("ReadOperation done, ${blocks} blocks in ${blocks + retransmissions} transmission(s).")
        }
        transferDone(responseQueue);
    }
}

// DSKIO read
