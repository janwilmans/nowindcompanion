package nl.myquest.nowindcompanion

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

public enum class DetectedNowindVersion {
    None { override fun toString() : String { return "None" } },
    V1 { override fun toString() : String { return "Nowind interface V1" } },
    V2 { override fun toString() : String { return "Nowind interface V2" } }
}

public enum class NowindCommand(val value: Int) {
    DSKIO (0x80),
    DSKCHG( 0x81),
    GETDPB( 0x82),
    CHOICE( 0x83),
    DSKFMT( 0x84),
    DRIVES( 0x85),
    INIENV( 0x86),
    GETDATE( 0x87),
    DEVICEOPEN( 0x88),
    DEVICECLOSE( 0x89),
    DEVICERNDIO( 0x8a),
    DEVICEWRITE( 0x8b),
    DEVICEREAD( 0x8c),
    DEVICEEOF( 0x8d),
    AUXIN ( 0x8e),
    AUXOUT( 0x8f),
    MESSAGE( 0x90),
    CHANGEIMAGE( 0x91),
    GETDOSVERSION( 0x92),
    CMDREQUEST( 0x93),
    BLOCKREAD( 0x94),
    BLOCKWRITE( 0x95),
    CPUINFO( 0x96),
    COMMAND( 0x97),
    STDOUT( 0x98)
}

class NowindViewModel(
    private val savedStateHandle : SavedStateHandle
) : ViewModel() {

    var messages : MutableState<MessageList> = mutableStateOf(MessageList())
    var deviceInfo : MutableState<DeviceInfo> = mutableStateOf(DeviceInfo())
    var dataReadLight : MutableState<Boolean> = mutableStateOf(false)
    var dataWriteLight : MutableState<Boolean> = mutableStateOf(false)
    var networkConnectionLight : MutableState<Boolean> = mutableStateOf(false)

    fun lightsOff()
    {
        viewModelScope.launch {
            dataReadLight.value = false
            dataWriteLight.value = false
            networkConnectionLight.value = false
        }
    }

    fun setReading(value: Boolean)
    {
        viewModelScope.launch {
            dataReadLight.value = value
        }
    }

    fun setWriting(value: Boolean)
    {
        viewModelScope.launch {
            dataWriteLight.value = value
        }
    }

    fun setNetworkActive(value: Boolean)
    {
        viewModelScope.launch {
            networkConnectionLight.value = value
        }
    }

    fun setDeviceInfo(info: DeviceInfo)
    {
        viewModelScope.launch {
            deviceInfo.value = info
        }
    }

    fun write(message : String)
    {
        val time = Calendar.getInstance().time
        val formatter = SimpleDateFormat("HH:mm:ss.SSS")
        val now = formatter.format(time)

        viewModelScope.launch {
            messages.value = messages.value.add(message)
        }
    }
}