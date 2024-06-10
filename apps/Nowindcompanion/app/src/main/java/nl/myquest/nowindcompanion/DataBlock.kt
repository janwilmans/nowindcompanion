package nl.myquest.nowindcompanion

import java.lang.IllegalStateException

class DataBlock(val data: List<Int>) {
    val marker = findMarker()

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
