package com.example.nowindcompanion

import android.content.Context
import android.os.Debug
import android.util.Log
import com.ftdi.j2xx.D2xxManager
import com.ftdi.j2xx.D2xxManager.FtDeviceInfoListNode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class FTDIClient (
    private val context: Context,
    private var viewModel: NowindViewModel,
    private var ftD2xx: D2xxManager =  D2xxManager.getInstance(context)
) : FTDI_Interface {

    override fun getIncomingDataUpdates(): Flow<ByteArray> {

        GlobalScope.launch {

            var foundDevices = 0
            while (true) {
                var numberOfDevices = ftD2xx.createDeviceInfoList(context)
                val deviceList = arrayOfNulls<FtDeviceInfoListNode>(numberOfDevices)
                ftD2xx.getDeviceInfoList(numberOfDevices, deviceList)

                viewModel.write(" FTDI <POLLING> ")

                if (numberOfDevices != foundDevices) {
                    viewModel.write("Found $numberOfDevices FTDI OTG devices")
                    foundDevices = numberOfDevices
                    if (foundDevices == 0)
                        viewModel.setVersion(NowindViewModel.DetectedNowindVersion.None)
                    else
                        viewModel.setVersion(NowindViewModel.DetectedNowindVersion.V2)
                }
                delay(2000) // pause for 5 seconds before running the loop again
            }
        }
        return callbackFlow {
            if (!context.hasNowindPermissions())
            {
                throw Exception("Missing Nowind Permissions")
            }
        }
    }
}



