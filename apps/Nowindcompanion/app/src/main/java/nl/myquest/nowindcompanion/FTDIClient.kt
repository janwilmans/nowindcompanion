package nl.myquest.nowindcompanion

import android.content.Context
import android.content.pm.PackageManager
import com.ftdi.j2xx.D2xxManager
import com.ftdi.j2xx.D2xxManager.FtDeviceInfoListNode
import com.ftdi.j2xx.FT_Device
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

const val HARDCODED_READ_DATABLOCK_SIZE = 128

// the host serves one disk, drive A: of the Nowind interface
const val NUMBER_OF_DRIVES = 1

// the only API of the COMMAND (extended BIOS) command
const val API_NOWMAP = 0

// these come in bulk (per sector or per character), they are not logged one by one
private val quietCommands = setOf(
    NowindCommand.DSKIO, NowindCommand.STDOUT, NowindCommand.AUXOUT, NowindCommand.DEVICEWRITE, NowindCommand.DEVICEREAD
)

// strips a string from outer double-quotes and anything outside them, 'pre("foo")bar' becomes 'foo'
fun stripQuotes(text: String): String {
    val first = text.indexOf('"')
    if (first < 0) return text
    val last = text.lastIndexOf('"')
    if (first == last) return ""
    return text.substring(first + 1, last)
}

// nowmap <drive_id> <partition> [/Nx] [/L]: lists the drives, or selects the partition of a harddisk image.
// The host serves floppy disk images only, they have no partitions.
fun nowMap(arguments: String, driveOffset: Int, hostedDisk: String?): String {
    val usage = "usage: nowmap <drive_id> <partition> [/Nx] [/L]\r\n"
    val listing = StringBuilder("Nowind Map v1.2\r\n")
    var letter = 'A'
    repeat(driveOffset) { listing.append("Drive ${letter++}: DiskRom\r\n") }
    repeat(NUMBER_OF_DRIVES) { listing.append("Drive ${letter++}, driveId $it (${hostedDisk ?: "no disk"})\r\n") }
    listing.append("\r\n")

    val words = arguments.uppercase().split(' ', '\t').filter { it.isNotEmpty() }
    if ("/L" in words) return listing.toString()
    val args = words.filter { !it.startsWith("/") }
    if (args.size != 2) return "error: no 2 arguments found\r\n$usage"
    val driveId = args[0].toIntOrNull()
    if (driveId == null || driveId !in 0 until NUMBER_OF_DRIVES) return "error: drive_id invalid!\r\n$usage"
    return "error: drive is not a hdd!\r\n$usage"
}

// install https://plugins.jetbrains.com/plugin/7017-plantuml-integration
// to view
/*
@startuml
Application <|-- NowindApp
NowindApp -> FTDIClient
NowindApp -> HostState
HostState -d-> HomeScreen
HostState -d-> DebugScreen
FTDIClient -- HostState
FTDIClient -d-* CommandQueue
FTDIClient -d-* ResponseQueue
FTDIClient --* diskimage
FTDIClient : waitForDevicePresent()
FTDIClient : host()
@enduml
*/

// https://www.amazon.nl/USB-OTG-Oplaadpoort-Compatibel-Chromecast/dp/B0B5MPCJF5/ref=asc_df_B08C5FWQND/?tag=nlshogostdde-21&linkCode=df0&hvadid=624714333587&hvpos=&hvnetw=g&hvrand=3005483864684403508&hvpone=&hvptwo=&hvqmt=&hvdev=c&hvdvcmdl=&hvlocint=&hvlocphy=9064064&hvtargid=pla-969368052238&mcid=eee95ed571413bc8b618785591a38bd9&th=1


data class DeviceInfo(
    val version: DetectedNowindVersion = DetectedNowindVersion.None,
    val serial: String = "",
    val description: String = ""
)

// the reply to GETDOSVERSION: 2 enables DOS2, which only exists on MSX2 and newer, 1 enables DOS1
fun dosVersionFor(msxIdByte: Int, enableDos2: Boolean): Int =
    if (enableDos2 && msxIdByte >= MsxVersion.Two.value) 2 else 1

class FTDIClient(
    private val context: Context,
    private val state: HostState,
    private var ftD2xx: D2xxManager = D2xxCompat.getInstance(context)
) {

    private val commandQueue = CommandQueue()    // Host << MSX
    private val responseQueue = ResponseQueue()  // Host >> MSX

    private var diskimage: ByteArray = ByteArray(1024 * 1024)
    private var diskChanged = true
    private var diskWasAbsent = false

    // receives a copy of the disk after a DSKIO write; null when the disk can not be saved, it is then write protected
    private var saveDisk: ((ByteArray) -> Unit)? = null

    // the number of drives of the disk roms that initialized before the Nowind interface, reported in DRIVES
    private var driveOffset = 0

    // characters printed by the MSX, logged per line
    private val stdout = StringBuilder()
    private val auxOut = StringBuilder()

    // the storage of the NOW: device, null until it is set up
    @Volatile
    var deviceFiles: DeviceFiles? = null
    private val nowDevice = NowDevice { deviceFiles }

    // CHANGEIMAGE (CALL IMAGE on the MSX) asks for a disk from the library by name
    @Volatile
    var onChangeImage: ((String) -> Unit)? = null

    @Volatile
    var enableDos2 = false

    private class PendingDisk(val image: ByteArray, val save: ((ByteArray) -> Unit)?)

    // set from the UI thread, swapped in by the hosting thread between commands
    private val pendingDisk = AtomicReference<PendingDisk?>(null)

    // a firmware update or verify, run by the hosting thread instead of hosting, see withFlashLink
    private class FlashJob(val block: (FlashLink) -> Any?) {
        val done = CompletableDeferred<Any?>()
    }

    private val pendingFlash = AtomicReference<FlashJob?>(null)

    @Volatile
    private var running = false
    private var thread: Thread? = null

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
        return DeviceInfo(DetectedNowindVersion.V2, node.serialNumber.toString(), node.description.toString())
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

    // returns null when the client is stopped before a device is found
    private fun waitForDevicePresent(): DeviceInfo? {
        if (hasOTGFeature(context)) {
            state.write("OTG Supported!")
        } else {
            state.write("OTG is not supported!")
            state.connection.value = ConnectionState.NoUsbHost
        }
        state.write("Ready and waiting...")
        var foundDevices = 0
        while (running) {
            val numberOfDevices = ftD2xx.createDeviceInfoList(context)
            val deviceList = arrayOfNulls<FtDeviceInfoListNode>(numberOfDevices)
            ftD2xx.getDeviceInfoList(numberOfDevices, deviceList)
            if (foundDevices != numberOfDevices) {
                state.write("Found $numberOfDevices FTDI OTG devices")
                foundDevices = numberOfDevices
            }
            if (foundDevices == 0) {
                state.deviceInfo.value = DeviceInfo()
            } else {
                val info = getDeviceInfoOpt(deviceList[0])
                state.deviceInfo.value = info
                return info
            }
            Thread.sleep(500)
        }
        return null
    }
//
//    private suspend fun first_level2() {
//        println("first_level2 entry")
//        while (true) {
//            println("first_level2 loop, threadid: '${Thread.currentThread().getId()}'")
//            yield();
//        }
//    }
//
//    private suspend fun first() {
//        println("first () entry, threadid: '${Thread.currentThread().getId()}'")
//        first_level2();
//    }
//
//
//    private suspend fun second_level2() {
//        println("second_level2 () entry")
//        while (true) {
//            println("second_level2 loop, threadid: '${Thread.currentThread().getId()}'")
//            yield();
//        }
//    }
//
//    private suspend fun second() {
//        println("second() entry, threadid: '${Thread.currentThread().getId()}'")
//        second_level2()
//    }
//
//    private fun host_example() = runBlocking {
//        val job1 = async { first() }
//        val job2 = async { second() }
//        while (true) {
//            job1.await();
//            job2.await();
//        }
//    }

    // logs the first bytes of every chunk exchanged with the interface (RX = MSX -> host, TX = host -> MSX)
    private fun logRawBytes(direction: String, bytes: List<Int>) {
        if (!Debug.enabled) return
        val maxShown = 32
        val shown = bytes.take(maxShown).joinToString(" ") { "%02X".format(it) }
        val more = if (bytes.size > maxShown) " ... (+${bytes.size - maxShown} more)" else ""
        Debug.log("$direction ${bytes.size} bytes: $shown$more")
    }

    private suspend fun open_and_run_ftdi_io() {

        val ftdiDevice = ftD2xx.openByIndex(context, 0)
        if (ftdiDevice == null || !ftdiDevice.isOpen) {
            state.write("Error opening device at index 0!")
            state.connection.value = ConnectionState.OpenFailed
            Thread.sleep(2000)
            return
        }
        try {
            run_ftdi_io(ftdiDevice)
        } finally {
            ftdiDevice.close()
        }
    }

    private suspend fun run_ftdi_io(ftdiDevice: FT_Device) {
        ftdiDevice.setLatencyTimer(16.toByte())
        val readAndWriteBuffer = 3.toByte()
        ftdiDevice.purge(readAndWriteBuffer)
        ftdiDevice.setLatencyTimer(1)

        val readTimeout = ftdiDevice.readTimeout
        val latencyTimer = ftdiDevice.latencyTimer
        state.write("readTimeout: ${readTimeout}ms")
        state.write("latencyTimer: ${latencyTimer}ms")
        state.write("Start hosting...")
        state.connection.value = ConnectionState.Hosting

        // poll quickly while the MSX is active, then back off exponentially to save battery
        val minPollDelayMs = 1L
        val maxPollDelayMs = 250L
        var pollDelayMs = minPollDelayMs

        while (running) {
            pendingFlash.getAndSet(null)?.let {
                runFlashJob(ftdiDevice, it)
                pollDelayMs = minPollDelayMs
                continue
            }
            val receivedBytes = ftdiDevice.queueStatus
            if (receivedBytes == -1) // we lost the connection
            {
                Debug.log("io: receivedBytes -1, connection lost...")
                return
            }
            //println("poll.. queueStatus...")
            if (receivedBytes == 0) {
                // delay would release control to 'readHandler' but we want to actually do nothing.
                Thread.sleep(pollDelayMs)
                pollDelayMs = minOf(pollDelayMs * 2, maxPollDelayMs)
                continue
            }
            pollDelayMs = minPollDelayMs

            try {
                //println("receivedBytes: ${receivedBytes}")
                val buffer = ByteArray(receivedBytes)
                val read = ftdiDevice.read(buffer, receivedBytes)
                if (read < 0) {
                    Debug.log("io: read returned $read, connection lost...")
                    return
                }
                val data = buffer.copyOf(read)
                logRawBytes("RX", data.map { it.toInt() and 0xFF })
                commandQueue.add(data)

                // yield to handle the commandQueue (effectively switching to 'readHandler')
                yield()

                val response = responseQueue.GetResponse()
                if (response.isNotEmpty()) logRawBytes("TX", response.map { it.toInt() and 0xFF })
                ftdiDevice.write(response)

            } catch (e: Exception) {
                Debug.error("run_ftdi_io exception: ${e.message}")
            }
        }
    }

    // the MSX runs the flash writer instead of the Nowind driver, so nothing else may use the interface meanwhile
    private fun runFlashJob(ftdiDevice: FT_Device, job: FlashJob) {
        val readAndWriteBuffer = 3.toByte()
        ftdiDevice.purge(readAndWriteBuffer)
        try {
            job.done.complete(job.block(DeviceLink(ftdiDevice)))
        } catch (e: Exception) {
            job.done.completeExceptionally(e)
        }
        // the MSX starts over after a reset, bytes of the flash writer must not be read as Nowind commands
        ftdiDevice.purge(readAndWriteBuffer)
        commandQueue.clear()
    }

    private class DeviceLink(private val device: FT_Device) : FlashLink {
        override fun write(data: ByteArray) {
            val written = device.write(data)
            if (written != data.size) throw IOException("writing to the interface failed ($written of ${data.size} bytes)")
        }

        override fun read(count: Int, timeoutMs: Long): ByteArray {
            val data = ByteArray(count)
            var received = 0
            val deadline = System.currentTimeMillis() + timeoutMs
            while (received < count) {
                val available = device.queueStatus
                if (available < 0) throw IOException("the interface was disconnected")
                if (available == 0) {
                    if (System.currentTimeMillis() > deadline) throw IOException("no answer from the MSX")
                    Thread.sleep(1)
                    continue
                }
                val chunk = ByteArray(minOf(available, count - received))
                val read = device.read(chunk, chunk.size)
                if (read < 0) throw IOException("reading from the interface failed")
                chunk.copyInto(data, received, 0, read)
                received += read
            }
            return data
        }

        override fun usbInfo(): List<Pair<String, String>> {
            val info = mutableListOf<Pair<String, String>>()
            device.deviceInfo?.let { node ->
                info += "USB chip" to ftdiType(node.type)
                info += "USB id" to "VID 0x%04X, PID 0x%04X".format(node.id ushr 16, node.id and 0xFFFF)
                info += "USB description" to node.description.orEmpty()
                info += "USB serial" to node.serialNumber.orEmpty()
                info += "USB chip revision" to "0x%04X".format(node.bcdDevice.toInt() and 0xFFFF)
            }
            // an interface without a programmed eeprom has nothing to report
            try {
                device.eepromRead()?.let { eeprom ->
                    info += "EEPROM manufacturer" to eeprom.Manufacturer.orEmpty()
                    info += "EEPROM product" to eeprom.Product.orEmpty()
                    info += "EEPROM max power" to "${eeprom.MaxPower} mA" + if (eeprom.SelfPowered) ", self powered" else ", bus powered"
                }
            } catch (e: Exception) {
                Debug.log("eepromRead failed: $e")
            }
            info += "d2xx library" to "0x%08X".format(D2xxManager.getLibraryVersion())
            return info
        }

        private fun ftdiType(type: Int) = when (type) {
            D2xxManager.FT_DEVICE_232B -> "FT232B"
            D2xxManager.FT_DEVICE_8U232AM -> "FT8U232AM"
            D2xxManager.FT_DEVICE_245R -> "FT245R / FT232R"
            D2xxManager.FT_DEVICE_2232 -> "FT2232"
            D2xxManager.FT_DEVICE_2232H -> "FT2232H"
            D2xxManager.FT_DEVICE_232H -> "FT232H"
            else -> "type $type"
        }
    }

    // runs block on the hosting thread with direct access to the interface; suspends until it is done
    suspend fun <T> withFlashLink(block: (FlashLink) -> T): T {
        if (state.connection.value != ConnectionState.Hosting) throw IOException("the Nowind interface is not connected")
        val job = FlashJob(block)
        if (!pendingFlash.compareAndSet(null, job)) throw IOException("a firmware update is already running")
        @Suppress("UNCHECKED_CAST")
        return job.done.await() as T
    }

    private fun host() = runBlocking {
        val handler_job = launch {
            while (true) {
                readHandler(commandQueue)
            }
        }
        open_and_run_ftdi_io()
        pendingFlash.getAndSet(null)?.done?.completeExceptionally(IOException("the Nowind interface was disconnected"))
        // the handler may be waiting for MSX data that will never arrive, cancel it at its next yield()
        handler_job.cancelAndJoin()
        commandQueue.clear()
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

        while (commandQueue.isEmpty()) yield()

        val size = commandQueue.size()
        try {

            //println("readHandler, ${size} bytes: ${commandQueue}, threadid: '${Thread.currentThread().getId()}'")
            commandQueue.waitFor(listOf(0xAF, 0x05))
            commandQueue.waitForBytes(9)

            val bc = commandQueue.readWord()
            val de = commandQueue.readWord()
            val hl = commandQueue.readWord()
            val f = commandQueue.readByte()
            val a = commandQueue.readByte()
            val cmd = commandQueue.readByte()
            handleCommand(Command(bc, de, hl, f, a, cmd))

        } catch (e: Exception) {
            when (e) {
                is TimeoutException -> {
                    Debug.log("readHandler TimeoutException: ${e.message}, queue: ${commandQueue}")
                    Debug.error("${e.message}, %d bytes remaining in queue were removed.".format(commandQueue.size()))
                    commandQueue.clear()
                    responseQueue.clear()
                    // ignored intentionally
                }

                is IOException -> {
                    Debug.log("readHandler IOException: ${e.message}, queue: ${commandQueue}")
                    Debug.error("Nowind command de-sync, %d bytes remaining in queue were removed.".format(commandQueue.size()))
                    commandQueue.clear()
                    responseQueue.clear()
                    // ignored intentionally
                }

                is kotlinx.coroutines.CancellationException -> throw e
                else -> {
                    Debug.log("readHandler exception: $e, queue: ${commandQueue}")
                    Debug.error("Nowind command failed:$e, %d bytes remaining in queue were removed.".format(commandQueue.size()))
                    commandQueue.clear()
                    responseQueue.clear()
                }
            }
        }
    }

    private fun le16(offset: Int): Int = (diskimage[offset].toInt() and 0xFF) + 256 * (diskimage[offset + 1].toInt() and 0xFF)

    // converts the boot sector (sector 0) into an MSX Disk Parameter Block, see GETDPB in protocol.md
    private fun getDpb(command: Command) {
        if (diskimage.size < 512) return
        val bytesPerSector = le16(11)
        val sectorsPerCluster = diskimage[13].toInt() and 0xFF
        val firstFatSector = le16(14)
        val fatCopies = diskimage[16].toInt() and 0xFF
        val rootDirEntries = le16(17)
        var numberOfSectors = le16(19)
        val mediaType = diskimage[21].toInt() and 0xFF
        val sectorsPerFat = le16(22)
        if (numberOfSectors == 0) {
            numberOfSectors = le16(32) + 65536 * le16(34)
        }
        if (bytesPerSector < 32 || sectorsPerCluster == 0) {
            Debug.log("GETDPB: invalid boot sector, ignored")
            return
        }

        val firstDirSector = (firstFatSector + fatCopies * sectorsPerFat) and 0xFFFF
        val directorySizeInSectors = (rootDirEntries * 32 + bytesPerSector - 1) / bytesPerSector
        val firstRecordSector = (firstDirSector + directorySizeInSectors) and 0xFFFF
        val maxClusters = (((numberOfSectors - firstRecordSector) / sectorsPerCluster) + 1) and 0xFFFF

        val dirMask = (bytesPerSector / 32) - 1
        var dirShift = 0
        while (dirMask and (1 shl dirShift) != 0) dirShift++
        val clusMask = sectorsPerCluster - 1
        var clusShift = 0
        while (clusMask and (1 shl clusShift) != 0) clusShift++

        responseQueue.addHeader()
        responseQueue.add(command.getE())
        responseQueue.add(command.getD())
        responseQueue.add(mediaType)                    // 1 ID
        responseQueue.add16(bytesPerSector)             // 2-3 SECSIZ
        responseQueue.add(dirMask and 0xFF)             // 4 DIRMSK
        responseQueue.add(dirShift)                     // 5 DIRSHFT
        responseQueue.add(clusMask and 0xFF)            // 6 CLUSMSK
        responseQueue.add(clusShift + 1)                // 7 CLUSSHFT
        responseQueue.add16(firstFatSector)             // 8-9 FIRFAT
        responseQueue.add(fatCopies)                    // 10 FATCNT
        responseQueue.add(rootDirEntries and 0xFF)      // 11 MAXENT
        responseQueue.add16(firstRecordSector)          // 12-13 FIRREC
        responseQueue.add16(maxClusters)                // 14-15 MAXCLUS
        responseQueue.add(sectorsPerFat and 0xFF)       // 16 FATSIZ
        responseQueue.add16(firstDirSector)             // 17-18 FIRDIR
    }

    // the new image is used from the next command on, the MSX sees it as a disk change.
    // Without save the disk is write protected.
    fun insertDisk(image: ByteArray, save: ((ByteArray) -> Unit)? = null) {
        pendingDisk.set(PendingDisk(image, save))
    }

    private suspend fun handleCommand(command: Command) {
        state.commands.update { it + 1 }
        pendingDisk.getAndSet(null)?.let {
            diskimage = it.image
            saveDisk = it.save
            diskChanged = true
        }
        if (command.toEnum() !in quietCommands) {
            state.write("$command")
        }

        when (command.toEnum()) {
            NowindCommand.DSKIO -> diskIo(command)
            NowindCommand.DSKCHG -> if (knownDrive(command) && diskPresent(command)) diskChange()
            NowindCommand.GETDPB -> if (knownDrive(command) && diskPresent(command)) getDpb(command)

            NowindCommand.DRIVES -> {
                driveOffset = command.a
                responseQueue.addHeader()
                responseQueue.add(0);  // 0 = no phantom drive, 2 = enable phantom drive(s)
                val allowOtherDiskroms = 0x80; // 0 = disabled, 0x80 = initialize more disk roms
                responseQueue.add(command.a or allowOtherDiskroms)
                responseQueue.add(NUMBER_OF_DRIVES)
            }

            NowindCommand.INIENV -> {
                responseQueue.addHeader()
                responseQueue.add(255); // index of romdisk (255 = no romdisk)
            }

            NowindCommand.GETDATE -> {
                val now = Calendar.getInstance()
                responseQueue.addHeader()
                responseQueue.add(now.get(Calendar.DAY_OF_MONTH))
                responseQueue.add(now.get(Calendar.MONTH) + 1)
                responseQueue.add16(now.get(Calendar.YEAR))
            }

            NowindCommand.DEVICEOPEN -> nowDevice.open(command, commandQueue.readBytes(11), responseQueue)
            NowindCommand.DEVICECLOSE -> nowDevice.close(command.hl)
            NowindCommand.DEVICEWRITE -> nowDevice.write(command)
            NowindCommand.DEVICEREAD -> nowDevice.read(command, responseQueue)

            // the MSX reads two replies: the first tells the host is there, the second brings the character.
            // The C++ host waits for a key on its console; a phone has none, so the answer is end of file.
            NowindCommand.AUXIN -> {
                responseQueue.addHeader()
                responseQueue.addHeader()
                responseQueue.add(0x1A)
            }

            NowindCommand.AUXOUT -> collectLine(auxOut, command.a, "MSX AUX> ", setOf(0x0D, 0x0A))
            NowindCommand.STDOUT -> collectLine(stdout, command.a, "MSX says> ", setOf(0x0D, 0x0A, 0, '$'.code))

            // a zero terminated text follows, sent by the USB_DBMSG macro of the ROM
            NowindCommand.MESSAGE -> {
                val text = commandQueue.readUntil(setOf(0), 239).takeWhile { it != 0 }
                state.write("MSX debug message: ${toText(text)}, registers: $command")
            }

            // CALL IMAGE("GAME.DSK") or nic.com; the name ends with 0 or ':'.
            // A is not a drive number here (nic.com sends the image type), the host has one drive anyway.
            NowindCommand.CHANGEIMAGE -> {
                val name = commandQueue.readUntil(setOf(0, ':'.code), 40).takeWhile { it != 0 && it != ':'.code }
                val fileName = stripQuotes(toText(name)).trim()
                state.write("CHANGEIMAGE: '$fileName'")
                if (fileName.isNotEmpty()) onChangeImage?.invoke(fileName)
            }

            NowindCommand.GETDOSVERSION -> {
                val msxIdByte = command.a // MSX version number, 0=MSX1, 1=MSX2, 2=MSX2+, 3=MSX Turbo R

                // send back header + "1" to enable DOS1 and "2" to enable DOS2
                // some games, like Puyo Puyo, dont see to be compatible with the way
                // the nowind ROM 'skips' the DOS2 initialization, so DOS2 is a setting that is off by default
                responseQueue.addHeader()
                responseQueue.add(dosVersionFor(msxIdByte, enableDos2))
            }

            // the MSX asks whether the host has a command waiting for it to execute,
            // B = 0: at startup (C = 0 for the first request), B = 1: anytime
            NowindCommand.CMDREQUEST -> {
                val commandType = command.getB()
                val commandArgument = command.getC()
                Debug.log("- CMD REQUESTED: %X with argument %X".format(commandType, commandArgument))
                if (commandType == 0 && commandArgument == 0) {
                    // the MSX boots, files of the NOW: device that were left open will never be closed
                    nowDevice.closeAll()
                    nextBoot()
                }
                if (commandType == 0 || commandType == 1) {
                    responseQueue.addHeader()
                    responseQueue.add(0) // no more commands
                } else {
                    Debug.log("* unknown CMDREQUEST type $commandType, ignored")
                }
            }

            // 11 bytes (IX, IY, SP, slot selection, $FCC5, $FCC7) and 34 bytes of the stack, which start with the PC
            NowindCommand.CPUINFO -> {
                val info = commandQueue.readBytes(45)
                fun word(index: Int) = info[index] + 256 * info[index + 1]
                state.write(
                    "CPU info: PC:%04X AF:%04X BC:%04X DE:%04X HL:%04X IX:%04X IY:%04X SP:%04X, slots: %02X %04X %04X".format(
                        word(11), command.a * 256 + command.f, command.bc, command.de, command.hl,
                        word(0), word(2), (word(4) + 6) and 0xFFFF, info[6], word(7), word(9)
                    )
                )
            }

            NowindCommand.COMMAND -> apiCommand(command)

            // the ROM handles CHOICE, DSKFMT and the random I/O and EOF of the NOW: device itself, and DSKIO does
            // the block transfers; the MSX never sends these commands, so there is no defined reply
            NowindCommand.CHOICE,
            NowindCommand.DSKFMT,
            NowindCommand.DEVICERNDIO,
            NowindCommand.DEVICEEOF,
            NowindCommand.BLOCKREAD,
            NowindCommand.BLOCKWRITE -> Debug.log("* ${command.toEnum()} is handled by the MSX ROM, ignored, registers: $command")

            null -> Debug.log("* unknown command: %X ignored".format(command.cmd))
        }
    }

    // no response is legal and causes a 'Disk offline' on the MSX, like the C++ host does for an unknown drive
    private fun knownDrive(command: Command): Boolean {
        if (command.a < NUMBER_OF_DRIVES) return true
        Debug.log("${command.toEnum()} for drive ${command.a}, which does not exist, ignored")
        return false
    }

    // 'boot without disk' takes effect at a boot and lasts until the next one
    private fun nextBoot() {
        val skip = state.skipDisk.updateAndGet { it.atBoot() }
        if (skip == SkipDisk.ThisBoot) state.write("Booting without disk, the next boot finds the disk again")
    }

    // without disk the host does not answer, like the C++ host for an empty drive: the MSX times out
    private fun diskPresent(command: Command): Boolean {
        if (state.skipDisk.value == SkipDisk.ThisBoot) {
            diskWasAbsent = true
            Debug.log("${command.toEnum()} ignored, booted without disk")
            return false
        }
        if (diskWasAbsent) {
            // the disk is back, the MSX must read it again
            diskWasAbsent = false
            diskChanged = true
        }
        return true
    }

    private suspend fun diskIo(command: Command) {
        if (!knownDrive(command) || !diskPresent(command)) return
        val sectorAmount = command.getB()
        val size = sectorAmount * 512
        val address = command.getDestinationAddress()
        val sector = command.getStartSector()
        val position = sector.toLong() * 512
        val write = (command.f and 1) == 1   // CF (Carry Flag) means "Write" if set and "Read" when not set.
        val direction = if (write) "write" else "read"
        if (sectorAmount == 0 || position + size > diskimage.size) {
            // no response is legal, the MSX will time out and report a disk error (like the C++ host)
            Debug.log("DSKIO $direction of $sectorAmount sectors at sector $sector is outside the disk image, ignored")
            return
        }

        val start = System.nanoTime()
        Debug.log("DSKIO $direction transfer sector $sector at address ${String.format("0x%04X", address)}, $sectorAmount sectors")
        if (write) {
            if (address + size > 0x10000) {
                Debug.log("DSKIO write of $size bytes from 0x%04X wraps around the address space, ignored".format(address))
                return
            }
            val data = WriteOperation(address, size).execute(responseQueue, commandQueue, saveDisk == null)
            if (data == null) {
                Debug.log("  DSKIO write refused, the disk is write protected")
                return
            }
            data.copyInto(diskimage, position.toInt())
            saveDisk?.invoke(diskimage.copyOf())
        } else {
            val data = readDisk(diskimage, position.toInt(), size)
            ReadOperation(address, data).execute(responseQueue, commandQueue)
        }
        // includes waiting for the MSX to acknowledge every block, so it is the speed the MSX sees
        val elapsed = System.nanoTime() - start
        if (write) {
            state.writeStats.update { it.add(size.toLong(), elapsed) }
        } else {
            state.readStats.update { it.add(size.toLong(), elapsed) }
            state.recentReads.add(size.toLong(), elapsed)
        }
        Debug.log("  DSKIO $direction done in ${elapsed / 1_000_000} ms, ${formatSpeed(size / 1024.0 / (elapsed / 1e9))}")
    }

    private fun diskChange() {
        responseQueue.addHeader()
        if (diskChanged) {
            diskChanged = false
            responseQueue.add(255) // changed
            val mediaDescriptor = mediaDescriptor()
            Debug.log("DSKCHG returns mediaDescriptor ${mediaDescriptor}")
            responseQueue.add(mediaDescriptor)
        } else {
            responseQueue.add(0)   // not changed
            responseQueue.add(255) // dummy
        }
    }

    // the first byte of the FAT. The FAT follows the reserved sectors (boot sector offset 14), that is sector 1 on
    // nearly every disk; old MSX-DOS1 disks have no valid boot sector, their FAT is at sector 1 as well
    private fun mediaDescriptor(): Int {
        val validBootSector = diskimage.size >= 512 && le16(11) == 512
        val reservedSectors = if (validBootSector) le16(14) else 1
        val position = reservedSectors.coerceIn(1, 64) * 512
        return if (position < diskimage.size) diskimage[position].toInt() and 0xFF else 0xF9
    }

    private fun collectLine(buffer: StringBuilder, character: Int, prefix: String, lineEnds: Set<Int>) {
        if (character in lineEnds) {
            if (buffer.isNotEmpty()) {
                state.write("$prefix$buffer")
                buffer.clear()
            }
        } else if (buffer.length < 256) {
            buffer.append(character.toChar())
        }
    }

    private fun toText(bytes: List<Int>): String = bytes.map { it.toChar() }.joinToString("")

    // the extended BIOS API (EXTBIO): C = API, A = 4 (the EXTBIO function), B = length of the arguments that follow,
    // DE = MSX address for the zero terminated result text
    private suspend fun apiCommand(command: Command) {
        val api = command.getC()
        val length = command.getB()
        if (api != API_NOWMAP) {
            Debug.log("* unknown API command $api, ignored, registers: $command")
            return
        }
        if (command.a != 4 || length == 0) {
            Debug.log("* API_NOWMAP with EXTBIO function ${command.a} and $length bytes of arguments, ignored")
            return
        }
        val arguments = toText(commandQueue.readBytes(length).takeWhile { it != 0 })
        val result = nowMap(arguments, driveOffset, state.hostedDisk.value)
        Debug.log("nowmap '$arguments': $result")
        ReadOperation(command.de, result.map { it.code and 0xFF } + 0).execute(responseQueue, commandQueue)
    }

    // hosting runs on its own thread; host() is a blocking runBlocking loop that busy-polls the FTDI device
    fun start() {
        if (thread != null) return
        running = true
        thread = thread(name = "NowindThread") {
            while (running) {
                state.connection.value = ConnectionState.Searching
                waitForDevicePresent() ?: break
                host()
                state.deviceInfo.value = DeviceInfo()
                state.write("nowind disconnected!")
            }
            state.connection.value = ConnectionState.Stopped
        }
    }

    fun stop() {
        running = false
        thread?.join(3000)
        thread = null
    }
}
