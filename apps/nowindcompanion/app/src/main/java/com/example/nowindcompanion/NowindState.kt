package com.example.nowindcompanion

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
            val list: List<String> = _messages.value.let { it ?: emptyList() }
            var mutableList = list.takeLast(24).toMutableList();
            mutableList.add("$now: $message")
            _messages.value = mutableList
        }
    }
}