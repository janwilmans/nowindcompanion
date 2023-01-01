package com.example.nowindcompanion

import android.content.Context
import android.os.Debug
import android.util.Log
import androidx.compose.ui.tooling.preview.Device
import com.ftdi.j2xx.D2xxManager
import com.ftdi.j2xx.D2xxManager.FtDeviceInfoListNode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

class DeviceInfo(val version: DetectedNowindVersion = DetectedNowindVersion.None)
{
    var serial: String = ""
    var description: String = ""
}

class FTDIClient (
    private val context: Context,
    private var viewModel: NowindViewModel,
    private var ftD2xx: D2xxManager = D2xxManager.getInstance(context)
) : FTDI_Interface {

    public enum class HostState {
        Searching,
        Idle,
        Reading,
        Writing,
    }

    fun getDeviceInfo(node : D2xxManager.FtDeviceInfoListNode) : DeviceInfo
    {
        var info : DeviceInfo = DeviceInfo(DetectedNowindVersion.V2)
        info.serial = node.serialNumber.toString()
        info.description = node.description.toString()
        return info
    }

    fun getDeviceInfoOpt(node : D2xxManager.FtDeviceInfoListNode?) : DeviceInfo
    {
        if (node == null)
        {
            return DeviceInfo()
        }
        return getDeviceInfo(node)
    }

    suspend fun search() : DeviceInfo {

        viewModel.write("Ready and waiting...")
        while (true) {
            var numberOfDevices = ftD2xx.createDeviceInfoList(context)
            val deviceList = arrayOfNulls<FtDeviceInfoListNode>(numberOfDevices)
            ftD2xx.getDeviceInfoList(numberOfDevices, deviceList)

            viewModel.write("Found $numberOfDevices FTDI OTG devices")
            val foundDevices = numberOfDevices
            if (foundDevices == 0) {
                viewModel.setDeviceInfo(DeviceInfo())
            }
            else {
                val info = getDeviceInfoOpt(deviceList[0])
                viewModel.setDeviceInfo(info = info)
                return info
            }
            delay(250) // pause for 5 seconds before running the loop again
        }
    }

    init {
        GlobalScope.launch {

            while (true)
            {
                val info = search()
                viewModel.write("loop...")
            }
            //val device = ftD2xx.openBySerialNumber(info.serial)
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



