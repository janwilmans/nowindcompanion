package com.example.nowindcompanion

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

class NowindViewModel(
    private val savedStateHandle : SavedStateHandle
) : ViewModel() {

    var messages : MutableState<MessageList> = mutableStateOf(MessageList())
    var deviceInfo : MutableState<DeviceInfo> = mutableStateOf(DeviceInfo())

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