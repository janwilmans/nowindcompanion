package nl.myquest.nowindcompanion

import java.lang.IllegalStateException

// a block of at most 128 bytes; fast blocks (128 bytes) are sent reversed, slow blocks in normal order.
// transferAddress is the END address for a fast block (the MSX writes back to front) and the START address for a slow block.
class DataBlock(val data: List<Int>, val transferAddress: Int = 0, val fast: Boolean = true) {
    val marker = findMarker()
    var retries = 0

    // the protocol relies on the block size being smaller then 255 bytes,
    // because that means there is always an unused value in the block.
    private fun findMarker(): Int {
        require(data.size < 255)
        var occurring = BooleanArray(256)
        for (i in data) {
            occurring[i] = true
        }
        for (i in occurring.indices) {
            if (occurring[i] == false) {
                return i;
            }
        }
        throw IllegalStateException("GetMarker could not find unused marker")
    }
}
