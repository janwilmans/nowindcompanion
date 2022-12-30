import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class NowindState : ViewModel() {

    public enum class DetectedNowindVersion {
        None { override fun toString() : String { return "None" } },
        V1 { override fun toString() : String { return "Nowind interface V1" } },
        V2 { override fun toString() : String { return "Nowind interface V2" } }
    }

    val messages : MessageList = MessageList()
    val version : MutableState<DetectedNowindVersion> = mutableStateOf(DetectedNowindVersion.None)

    fun setVersion(_version: DetectedNowindVersion)
    {
        version.value = _version
    }

    fun write(message : String)
    {
        val time = Calendar.getInstance().time
        val formatter = SimpleDateFormat("HH:mm:ss.SSS")
        val now = formatter.format(time)
        messages.write("$now: $message")
    }
}