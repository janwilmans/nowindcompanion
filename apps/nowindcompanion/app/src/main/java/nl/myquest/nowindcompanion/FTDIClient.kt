package nl.myquest.nowindcompanion

import android.content.Context
import android.content.pm.PackageManager
import com.ftdi.j2xx.D2xxManager
import com.ftdi.j2xx.D2xxManager.FtDeviceInfoListNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.Integer.*
import java.net.URL
import java.util.LinkedList
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

    private val readQueue = Queue()  // Host << MSX

    fun getDeviceInfo(node : D2xxManager.FtDeviceInfoListNode) : DeviceInfo
    {
        val info = DeviceInfo(DetectedNowindVersion.V2)
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

    fun hasOTGFeature(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
    }

    suspend fun wait_for_device_present() : DeviceInfo {
        if (hasOTGFeature(context))
        {
            viewModel.write("OTG Supported!")
        }
        else
        {
            viewModel.write("OTG is not supported!")
        }
        viewModel.write("Ready and waiting...")
        var foundDevices = 0
        while (true) {
            try {
                viewModel.setReading(true)
                var numberOfDevices = ftD2xx.createDeviceInfoList(context)
                val deviceList = arrayOfNulls<FtDeviceInfoListNode>(numberOfDevices)
                ftD2xx.getDeviceInfoList(numberOfDevices, deviceList)
                if (foundDevices != numberOfDevices)
                {
                    viewModel.write("Found $numberOfDevices FTDI OTG devices")
                    foundDevices = numberOfDevices
                }
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
            delay(500)
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

        val device = ftD2xx.openByIndex(context,0)
        if (device == null)
        {
            viewModel.write("Error opening device at index 0!")
            return
        }
        if (device.isOpen() == false)
        {
            viewModel.write("Error opening device at index 0!")
            return
        }
        device.setLatencyTimer(16.toByte())
        val readAndWriteBuffer = 3.toByte()
        device.purge(readAndWriteBuffer)
        val readTimeout : Int = device.readTimeout
        viewModel.write("readTimeout: ${readTimeout}ms")

        viewModel.write("Start hosting...")
        while (true)
        {
            val receivedBytes = device.queueStatus
            if (receivedBytes == -1) // we lost the connection
            {
                return
            }
            if (receivedBytes > 0) {
                val data = ByteArray(receivedBytes)
                device.read(data)
                readQueue.add(data)
                readHandler(readQueue)
            }
            delay(250)
        }
    }

// received at boot:
//    AF05 C  B  E  D  L  H  F  A  CMD
//    AF05 00 00 01 10 C2 FC A4 F5 93 (11) // NowindCommand::CMDREQUEST
//    AF05 00 00 00 F5 C2 20 45 01 92 (11) // NowindCommand::GETDOSVERSION
//    AFFF AA 55 FFFF (6)                  // RAM Detection?
//    AF05 00 00 21 FB 00 00 00 00 85 (11) // NowindCommand::DRIVES
//    AF05 00 00 2F FD 02 76 7C C9 86 (11) // NowindCommand::INIENV
//    AA55 FFFF (4)                        // RAM Detection?


    fun commandToEnum(byteValue: Int): NowindCommand? {
        return enumValues<NowindCommand>().find { it.value == byteValue }
    }

    private suspend fun readHandler(queue: Queue) {
        queue.waitFor(0xAF)
        queue.waitFor(0x05)
        queue.waitForBytes(9)

        val BC = queue.readWord()
        val DE = queue.readWord()
        val HL = queue.readWord()
        val F = queue.readByte()
        val A = queue.readByte()
        val CMD = queue.readByte()
        val commandName = commandToEnum(CMD)?.name ?: "unknown"
        viewModel.write("$commandName (%H) BC=%04X, DE=%04X, HL=%04X, F=%X, A=%X".format(CMD, BC, DE, HL, F, A))
    }

    fun downloadFile(fileUrl: String, destinationFile: File) {
        try {
            if (destinationFile.exists())
            {
                viewModel.write("Found existing: $fileUrl")
            }
            else
            {
                viewModel.write("Download: $fileUrl")
                BufferedInputStream(URL(fileUrl).openStream()).use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val data = ByteArray(1024)
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            output.write(data, 0, count)
                        }
                    }
                }
                viewModel.write("Done.")
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
        //val path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val path = context.getExternalFilesDir(null);

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
                val info = wait_for_device_present()
                host(info)
                viewModel.write("nowind disconnected!")
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



