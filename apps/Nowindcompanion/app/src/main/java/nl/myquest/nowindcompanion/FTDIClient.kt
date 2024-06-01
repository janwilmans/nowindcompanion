package nl.myquest.nowindcompanion

import android.content.Context
import android.content.pm.PackageManager
import com.ftdi.j2xx.D2xxManager
import com.ftdi.j2xx.D2xxManager.FtDeviceInfoListNode
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

const val HARDCODED_READ_DATABLOCK_SIZE = 128

class DeviceInfo(val version: DetectedNowindVersion = DetectedNowindVersion.None) {
    var serial: String = ""
    var description: String = ""
}

@OptIn(DelicateCoroutinesApi::class)
class FTDIClient(
    private val context: Context,
    private var viewModel: NowindViewModel,
    private var ftD2xx: D2xxManager = D2xxManager.getInstance(context)
) : FTDI_Interface {

    enum class HostState {
        Searching,
        Idle,
        Reading,
        Writing,
    }

    private val commandQueue = CommandQueue()    // Host << MSX
    private val responseQueue = ResponseQueue()  // Host >> MSX

    private var diskimage: ByteArray = ByteArray(1024 * 1024)

    fun readDisk(diskData: ByteArray, position: Int, size: Int): List<Int> {
        require(position >= 0 && position < diskData.size) { "Invalid position" }
        require(size >= 0 && position + size <= diskData.size) { "Invalid size or exceeds disk size" }

        val result = mutableListOf<Int>()
        for (i in position until position + size) {
            result.add(diskData[i].toInt() and 0xFF)
        }
        return result
    }

    private fun getDeviceInfo(node: FtDeviceInfoListNode): DeviceInfo {
        val info = DeviceInfo(DetectedNowindVersion.V2)
        info.serial = node.serialNumber.toString()
        info.description = node.description.toString()
        return info
    }

    private fun getDeviceInfoOpt(node: FtDeviceInfoListNode?): DeviceInfo {
        if (node == null) {
            return DeviceInfo()
        }
        return getDeviceInfo(node)
    }

    private fun hasOTGFeature(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
    }

    private fun waitForDevicePresent(): DeviceInfo {
        if (hasOTGFeature(context)) {
            viewModel.write("OTG Supported!")
        } else {
            viewModel.write("OTG is not supported!")
        }
        viewModel.write("Ready and waiting...")
        var foundDevices = 0
        while (true) {
            try {
                viewModel.setReading(true)
                val numberOfDevices = ftD2xx.createDeviceInfoList(context)
                val deviceList = arrayOfNulls<FtDeviceInfoListNode>(numberOfDevices)
                ftD2xx.getDeviceInfoList(numberOfDevices, deviceList)
                if (foundDevices != numberOfDevices) {
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
            } finally {
                viewModel.setReading(false)
            }
            Thread.sleep(500)
        }
    }


    private suspend fun host() {

        val ftdiDevice = ftD2xx.openByIndex(context, 0)
        if (ftdiDevice == null || !ftdiDevice.isOpen) {
            viewModel.write("Error opening device at index 0!")
            delay(2000)
            return
        }
        ftdiDevice.setLatencyTimer(16.toByte())
        val readAndWriteBuffer = 3.toByte()
        ftdiDevice.purge(readAndWriteBuffer)
        val readTimeout: Int = ftdiDevice.readTimeout
        viewModel.write("readTimeout: ${readTimeout}ms")
        viewModel.write("Start hosting...")
        while (true) {
            val receivedBytes = ftdiDevice.queueStatus
            if (receivedBytes == -1) // we lost the connection
            {
                return
            }
            try {
                if (receivedBytes > 0) {
                    val data = ByteArray(receivedBytes)
                    ftdiDevice.read(data)
                    commandQueue.add(data)
                    readHandler(commandQueue)
                    val response = responseQueue.GetResponse()
                    println("response: %s".format(toHexString(response)))
                    ftdiDevice.write(response)

                    continue
                }
                delay(250)
            } catch (e: Exception) {
                when (e) {
                    is TimeoutException -> {
                        val message = e.message
                        println("$message, %d bytes remaining in queue.".format(commandQueue.size()))
                        commandQueue.clear()
                        // ignored intentionally
                    }

                    is IOException -> {
                        println("nowind command de-sync, %d bytes remaining in queue.".format(commandQueue.size()))
                        commandQueue.clear()
                        // ignored intentionally

                    }

                    else -> throw e
                }
            }
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

    private suspend fun readHandler(commandQueue: CommandQueue) {

        val size = commandQueue.size()
        println("readHandler, $size bytes: $commandQueue")
        commandQueue.waitFor(listOf(0xAF, 0x05))
        commandQueue.waitForBytes(9)

        val bc = commandQueue.readWord()
        val de = commandQueue.readWord()
        val hl = commandQueue.readWord()
        val f = commandQueue.readByte()
        val a = commandQueue.readByte()
        val cmd = commandQueue.readByte()
        handleCommand(Command(bc, de, hl, f, a, cmd))
    }

    private fun handleCommand(command: Command) {
        viewModel.write("$command")
        when (command.toEnum()) {
            NowindCommand.DSKIO -> {

                beep();

                // no response is legal and will cause a 'Disk Offline' to occur on the MSX
                // for example, this would be expected when a disk number is requested that is unknown
                if (command.f and 1 == 1)   // CF (Carry Flag) means "Write" if set and "Read" when not set.
                {
                    println("Writing not implemented!")
                    return
                }

                // DSKIO read

                val sectorAmount = command.getB()
                val size = sectorAmount * 512
                val address = command.getDestinationAddress();
                var sector = command.getStartSector()
                val diskimageReadPosition = sector * 512;
                val data = readDisk(diskimage, diskimageReadPosition, diskimageReadPosition + size)

                println("DSKIO read transfer $sector to address $address, $sectorAmount sectors")

                if (size < HARDCODED_READ_DATABLOCK_SIZE) {
                    println(" schedule 1 slow block of size $size")
                    // just 1 slow block
                    responseQueue.addHeader()
                    responseQueue.add(BlockRead.SLOWTRANSFER.value)
                    responseQueue.add16(address)
                    responseQueue.add16(size)
                    responseQueue.addBlock(data)
                } else {
                    // fast blocks are send in reverse order (end -> start)
                    val blockAmount = size / HARDCODED_READ_DATABLOCK_SIZE
                    println(" schedule $blockAmount fast block(s)")

                    responseQueue.addHeader()
                    responseQueue.add(BlockRead.FASTTRANSFER.value)
                    responseQueue.add16(address + size)
                    responseQueue.add(blockAmount)
                    responseQueue.addBlocks(data.reversed())
                }

            }

            NowindCommand.DSKCHG -> {
                responseQueue.addHeader()
                responseQueue.add(0)    // Say nothing changed (incomplete implementation)
                responseQueue.add(255)  // Dummy
            }

            NowindCommand.GETDPB -> TODO()
            NowindCommand.CHOICE -> TODO()
            NowindCommand.DSKFMT -> TODO()
            NowindCommand.DRIVES -> {
                responseQueue.addHeader()
                responseQueue.add(0);  // 0 = no phantom drive, 2 = enable phantom drive(s)
                val allowOtherDiskroms = 0x80; // 0 = disabled, 0x80 = initialize more disk roms
                responseQueue.add(command.a or allowOtherDiskroms)
                val numberOfDrives = 1
                responseQueue.add(numberOfDrives)
            }

            NowindCommand.INIENV -> {
                responseQueue.addHeader()
                responseQueue.add(255); // index of romdisk (255 = no romdisk)
            }

            NowindCommand.GETDATE -> TODO()
            NowindCommand.DEVICEOPEN -> TODO()
            NowindCommand.DEVICECLOSE -> TODO()
            NowindCommand.DEVICERNDIO -> TODO()
            NowindCommand.DEVICEWRITE -> TODO()
            NowindCommand.DEVICEREAD -> TODO()
            NowindCommand.DEVICEEOF -> TODO()
            NowindCommand.AUXIN -> TODO()
            NowindCommand.AUXOUT -> TODO()
            NowindCommand.MESSAGE -> TODO()
            NowindCommand.CHANGEIMAGE -> TODO()
            NowindCommand.GETDOSVERSION -> {
                val msxIdByte = command.a // MSX version number, 0=MSX1, 1=MSX2, 2=MSX2+, 3=MSX Turbo R

                // send back header + "1" to enable DOS1 and "2" to enable DOS2
                // while this might be a configuration option, it makes sense to enable DOS2 only on MSX2
                // (because DOS2 is build-in in MSX Turbo R)
                responseQueue.addHeader()
                if (msxIdByte == MsxVersion.Two.value) {
                    // 2 will enable dos2, but will disable it for now,
                    // as games will not like dos2

                    responseQueue.add(1)
                } else {
                    responseQueue.add(1)
                }
            }

            // the MSX asks whether the host has a command waiting for it to execute
            NowindCommand.CMDREQUEST -> {
                val commandId = command.getB()
                val commandArgument = command.getC()
                println("- CMD REQUESTED: %X with argument %X".format(commandId, commandArgument))

                responseQueue.addHeader()
                responseQueue.add(0) // no more commands / not implemented
            }

            NowindCommand.BLOCKREAD -> TODO()
            NowindCommand.BLOCKWRITE -> TODO()
            NowindCommand.CPUINFO -> TODO()
            NowindCommand.COMMAND -> TODO()
            NowindCommand.STDOUT -> TODO()
            null -> println("* unknown command: %X ignored".format(command.cmd))
        }


    }

    private fun downloadFile(fileUrl: String, destinationFile: File) = try {
        if (destinationFile.exists()) {
            viewModel.write("Use cached: $fileUrl")
        } else {
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

    } catch (e: Exception) {
        println("### Exception: $e")
    }


    private fun load(zipFile: File) {
        if (!zipFile.exists()) {
            viewModel.write("File not found!")
            return
        }
        val inputStream = FileInputStream(zipFile)
        val zipInputStream = ZipInputStream(inputStream)

        var firstDiskLoaded = false

        var entry: ZipEntry? = zipInputStream.nextEntry
        while (entry != null) {
            val entryName = entry.name

            if (firstDiskLoaded) {
                viewModel.write("Skipping: $entryName")
            } else {
                viewModel.write("Loading: $entryName")

                val byteArrayOutputStream = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var len: Int
                while (zipInputStream.read(buffer).also { len = it } > 0) {
                    byteArrayOutputStream.write(buffer, 0, len)
                }
                diskimage = byteArrayOutputStream.toByteArray()
                firstDiskLoaded = true
            }
            entry = zipInputStream.nextEntry
        }
        zipInputStream.close()
        inputStream.close()
    }

    private fun showzip(zipFile: File) {
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

    private fun prepareDisks() {
        val path = context.getExternalFilesDir(null)

        //val puyo = "https://download.file-hunter.com/Games/MSX2/DSK/Puyo%20Puyo%20(en)%20(1991)%20(Compile).zip"
        val aleste2 = "https://download.file-hunter.com/Games/MSX2/DSK/Aleste%202%20(1988)(Compile)(en)(Disk2of3)(Woomb).zip"
        //downloadFile(puyo, File(path, "puyo.zip"))
        downloadFile(aleste2, File(path, "aleste2.zip"))
        //showzip(File(path, "puyo.zip"))
        //showzip(File(path, "aleste2.zip"))
        load(File(path, "aleste2.zip"))
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

        GlobalScope.launch(Dispatchers.Default) {

            while (true) {
                waitForDevicePresent()
                host()
                viewModel.write("nowind disconnected!")
            }
            //val device = ftD2xx.openBySerialNumber(info.serial)
        }
    }

    override fun getIncomingDataUpdates(): Flow<ByteArray> {

        return callbackFlow {
            if (!context.hasNowindPermissions()) {
                throw Exception("Missing Nowind Permissions")
            }
        }
    }


    fun allocateDirectExample() {
        // Specify the size of the byte buffer (in bytes)
        val bufferSize = 1024 * 1024 // 1 MB, adjust as needed

        // Allocate a direct ByteBuffer for continuous memory
        val byteBuffer = ByteBuffer.allocateDirect(bufferSize)

        // Use the byteBuffer for your operations

        // Release the allocated memory explicitly when done
        byteBuffer.clear()
    }

}
