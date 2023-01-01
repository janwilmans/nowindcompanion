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

class DeviceInfo(val serial: String = "", val version: DetectedNowindVersion = DetectedNowindVersion.None) {


}

class FTDIClient (
    private val context: Context,
    private var viewModel: NowindViewModel,
    private var ftD2xx: D2xxManager =  D2xxManager.getInstance(context)
) : FTDI_Interface {

    public enum class HostState {
        Searching,
        Idle,
        Reading,
        Writing,
    }

    suspend fun search() {

        var foundDevices = 0
        while (true) {
            var numberOfDevices = ftD2xx.createDeviceInfoList(context)
            val deviceList = arrayOfNulls<FtDeviceInfoListNode>(numberOfDevices)
            ftD2xx.getDeviceInfoList(numberOfDevices, deviceList)

            viewModel.write(" FTDI <POLLING> ")

            if (numberOfDevices != foundDevices) {
                viewModel.write("Found $numberOfDevices FTDI OTG devices")
                foundDevices = numberOfDevices
                if (foundDevices == 0) {
                    viewModel.setDeviceInfo(DeviceInfo())
                }
                else {
                    val info : DeviceInfo = DeviceInfo(deviceList[0]?.serialNumber.toString(), DetectedNowindVersion.V2)
                    viewModel.setDeviceInfo(info)
                }
            }
            delay(250) // pause for 5 seconds before running the loop again
        }
    }

    init {
        GlobalScope.launch {

            search()
        }
    }

    override fun getIncomingDataUpdates(): Flow<ByteArray> {

        return callbackFlow {
            if (!context.hasNowindPermissions())
            {
                throw Exception("Missing Nowind Permissions")
            }
        }
    }
}



