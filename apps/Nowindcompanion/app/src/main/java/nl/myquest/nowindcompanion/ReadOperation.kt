package nl.myquest.nowindcompanion

class ReadOperation(val address: Int, data: List<Int>) {
    var dataBlocks: List<DataBlock>

    init {
        dataBlocks = createDataBlocks(address, data)
    }

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

    private fun transfer(block: DataBlock, responseQueue: ResponseQueue) {
        //println("DSKIO read transfer sector $sector to address ${String.format("0x%04X", address)}, $sectorAmount sectors")
        val size = block.data.size
        if (size == 0) {
            println("transferBlock of size 0, this should never have been scheduled, doing nothing.")
            return
        }

        if (block.data.size < HARDCODED_READ_DATABLOCK_SIZE) {
            // just 1 slow block
            responseQueue.addHeader()
            responseQueue.add(BlockRead.SLOWTRANSFER.value)
            responseQueue.add16(address)
            responseQueue.add16(size)
            responseQueue.addBlock(block)
        } else {
            // fast blocks are send in reverse order (end -> start)
            val blockAmount = size / HARDCODED_READ_DATABLOCK_SIZE
            responseQueue.addHeader()
            responseQueue.add(BlockRead.FASTTRANSFER.value)
            responseQueue.add16(address + size)
            responseQueue.add(blockAmount)
            responseQueue.addBlocks(block.data.reversed(), HARDCODED_READ_DATABLOCK_SIZE)
        }
    }

    private suspend fun verify(block: DataBlock, commandQueue: CommandQueue): Boolean {
        commandQueue.restartTimeout()
        return (commandQueue.next() == block.marker)
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

            for (block in dataBlocks) {
                transfer(block, responseQueue)
            }

            var retransmit: MutableList<DataBlock> = mutableListOf()
            for (block in dataBlocks) {

                if (verify(block, commandQueue) == false) {
                    // block returned incorrect marker, schedule it for re-transmission
                    println("verify failed, schedule retransmission")
                    retransmit.add(block)
                    retransmissions = retransmissions + 1
                }
            }
            dataBlocks = retransmit
        }
        println("ReadOperation done, ${blocks} blocks in ${blocks + retransmissions} transmission(s).")
        transferDone(responseQueue);
    }
}

// DSKIO read
