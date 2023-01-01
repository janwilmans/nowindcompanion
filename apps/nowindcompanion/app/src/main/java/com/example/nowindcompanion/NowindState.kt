package com.example.nowindcompanion

import MessageList
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class NowindViewModel(
    private val savedStateHandle : SavedStateHandle
) : ViewModel() {

    public enum class DetectedNowindVersion {
        None { override fun toString() : String { return "None" } },
        V1 { override fun toString() : String { return "Nowind interface V1" } },
        V2 { override fun toString() : String { return "Nowind interface V2" } }
    }

    private val _messages : MutableLiveData<List<String>> = MutableLiveData()
    private val _version : MutableLiveData<DetectedNowindVersion> = MutableLiveData(DetectedNowindVersion.None)
    val messages: LiveData<List<String>> get() = _messages  // public read-only version
    var version: LiveData<DetectedNowindVersion> = _version // public read-only version

    fun setVersion(my_version: DetectedNowindVersion)
    {
        _version.postValue(my_version)
    }

    fun write(message : String)
    {
        val time = Calendar.getInstance().time
        val formatter = SimpleDateFormat("HH:mm:ss.SSS")
        val now = formatter.format(time)

        viewModelScope.launch {
            _messages.value = listOf<String>(message)
        }
    }
}