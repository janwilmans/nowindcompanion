package com.example.nowindcompanion

import android.content.Context
import android.os.Debug
import android.util.Log
import com.ftdi.j2xx.D2xxManager
import com.ftdi.j2xx.D2xxManager.FtDeviceInfoListNode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FTDIClient (
        private val context: Context,
        private var ftD2xx: D2xxManager =  D2xxManager.getInstance(context)
        ) : FTDI_Interface {

    override fun getIncomingDataUpdates(): Flow<ByteArray> {

        var numberOfDevices = ftD2xx.createDeviceInfoList(context)
        val deviceList = arrayOfNulls<FtDeviceInfoListNode>(numberOfDevices)
        ftD2xx.getDeviceInfoList(numberOfDevices, deviceList)
        Log.i("tag", "FTDI found $numberOfDevices devices")


        return callbackFlow {
            if (!context.hasNowindPermissions())
            {
                throw Exception("Missing Nowind Permissions")
            }


//            var callback = object: () -> ByteArray {
//
//                launch { send(byteArray) }
//                awaitClose
//                {
//                    // close ftdi connection
//                }
//
//            }


        }
    }
}

