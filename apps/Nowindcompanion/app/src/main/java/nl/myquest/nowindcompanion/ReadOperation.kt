package nl.myquest.nowindcompanion

class ReadOperation(address: Int, data: List<Int>) {
    val dataBlocks: List<DataBlock>

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

    public fun execute() {
        // send dataBlocks one-by-one
        //
    }
}

// DSKIO read


//println("DSKIO read transfer sector $sector to address ${String.format("0x%04X", address)}, $sectorAmount sectors")
//
//if (size < HARDCODED_READ_DATABLOCK_SIZE) {
//    println(" schedule 1 slow block of size $size")
//    // just 1 slow block
//    responseQueue.addHeader()
//    responseQueue.add(BlockRead.SLOWTRANSFER.value)
//    responseQueue.add16(address)
//    responseQueue.add16(size)
//    responseQueue.addBlock(DataBlock(data))
//} else {
//    // fast blocks are send in reverse order (end -> start)
//    val blockAmount = size / HARDCODED_READ_DATABLOCK_SIZE
//    println(" schedule $blockAmount fast block(s)")
//
//    responseQueue.addHeader()
//    responseQueue.add(BlockRead.FASTTRANSFER.value)
//    responseQueue.add16(address + size)
//    responseQueue.add(blockAmount)
//    responseQueue.addBlocks(data.reversed(), HARDCODED_READ_DATABLOCK_SIZE)
