package com.example.nowindcompanion

import kotlinx.coroutines.flow.Flow

interface FTDI_Interface {
    fun getIncomingDataUpdates() : Flow<ByteArray>
}