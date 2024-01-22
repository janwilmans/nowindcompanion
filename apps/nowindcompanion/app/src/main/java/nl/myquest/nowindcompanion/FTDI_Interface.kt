package nl.myquest.nowindcompanion

import kotlinx.coroutines.flow.Flow

interface FTDI_Interface {
    fun getIncomingDataUpdates() : Flow<ByteArray>
}