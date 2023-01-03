package com.example.nowindcompanion

import android.content.Context
import android.os.Environment
import com.ftdi.j2xx.D2xxManager
import com.ftdi.j2xx.D2xxManager.FtDeviceInfoListNode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


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
            try {
                viewModel.setReading(true)
                var numberOfDevices = ftD2xx.createDeviceInfoList(context)
                val deviceList = arrayOfNulls<FtDeviceInfoListNode>(numberOfDevices)
                ftD2xx.getDeviceInfoList(numberOfDevices, deviceList)

                viewModel.write("Found $numberOfDevices FTDI OTG devices")
                val foundDevices = numberOfDevices
                if (foundDevices == 0) {
                    viewModel.setDeviceInfo(DeviceInfo())
                } else {
                    val info = getDeviceInfoOpt(deviceList[0])
                    viewModel.setDeviceInfo(info = info)
                    return info
                }
            }
            finally {
                viewModel.setReading(false)
            }
            delay(250) // pause for 5 seconds before running the loop again
        }
    }

    fun hexString(bytes: ByteArray): String {
        val hexArray = "0123456789ABCDEF".toCharArray()
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    suspend fun host(info: DeviceInfo) {

        val device = ftD2xx.openByIndex(context,0);
        if (device == null)
        {
            viewModel.write("Error opening device at index 0!")
            return;
        }
        if (device.isOpen() == false)
        {
            viewModel.write("Error opening device at index 0!")
            return;
        }
        device.setLatencyTimer(16.toByte())
        val readAndWriteBuffer = 3.toByte()
        device.purge(readAndWriteBuffer)
        val readTimeout : Int = device.readTimeout
        viewModel.write("readTimeout: ${readTimeout}ms")

//        val writeData: String = "test"
//        val OutData = writeData.toByteArray()
//        val iLen: Int = device.write(OutData, writeData.length)
//
        viewModel.write("Start hosting...")
        while (true)
        {
            val receivedBytes = device.queueStatus;
            if (receivedBytes> 0) {
                val data = ByteArray(receivedBytes)
                device.read(data)
                viewModel.write(hexString(data))
                readHandler(data)
            }
            delay(250)
        }
    }



    private suspend fun readHandler(data: ByteArray) {
        var index = 0
        index = readHeader(index, data)
    }

    private suspend fun readHeader(index: Int, data: ByteArray): Int {
        val index = expect_data(0xaf, index, data)
        return expect_data(0x05, index, data)
    }

    private suspend fun expect_data(expected: Int, index: Int, data: ByteArray): Int {

        while (true)
        {
            if (index < data.size)
            {
                val value = data[index]
                if (expected.toByte() == value)
                {
                    return index + 1
                }
                else
                {
                    throw RuntimeException("$value was read where $expected was expected")
                }
            }
            yield()
        }
    }

    fun downloadFile(fileUrl: String, destinationFile: File) {
        try {
            if (!destinationFile.exists()) {
                BufferedInputStream(URL(fileUrl).openStream()).use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val data = ByteArray(1024)
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            output.write(data, 0, count)
                        }
                    }
                }
            }
        }
        catch (e: Exception)
        {
            println("### Exception: $e")
        }
    }

    fun showzip(zipFile : File)
    {
        if (!zipFile.exists()) {
            viewModel.write("File not found!")
            return
        }
        val inputStream = FileInputStream(zipFile)
        val zipInputStream = ZipInputStream(inputStream)

        var entry: ZipEntry? = zipInputStream.nextEntry
        while (entry != null) {
            val entryName = entry.name
            viewModel.write("Found: $entryName")
            // Read the data for the entry here...

            entry = zipInputStream.nextEntry
        }
        zipInputStream.close()
        inputStream.close()
    }

    fun prepareDisks()
    {
        val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        val puyo = "https://download.file-hunter.com/Games/MSX2/DSK/Puyo%20Puyo%20(en)%20(1991)%20(Compile).zip"
        val aleste2 = "https://download.file-hunter.com/Games/MSX2/DSK/Aleste%202%20(1988)(Compile)(en)(Disk2of3)(Woomb).zip"
        downloadFile(puyo, File(path, "puyo.zip"))
        downloadFile(aleste2, File(path, "aleste2.zip"))

        showzip(File(path, "puyo.zip"))
        showzip(File(path, "aleste2.zip"))
    }

    init {

        GlobalScope.launch(newSingleThreadContext("DownloadThread"))
        {
            prepareDisks()
            withContext(Dispatchers.Main)
            {
                viewModel.write("disks loaded...")
            }
        }

//        runBlocking {
//            launch (Dispatchers.IO)
//            {
//                delay(2000) // delay 1
//            }
//            launch (Dispatchers.IO)
//            {
//                delay(2000) // delay will run parallel to delay 1!!
//            }
//            delay(2000)
//        }

        GlobalScope.launch(Dispatchers.Default) {

            while (true)
            {
                val info = search()
                host(info)
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



