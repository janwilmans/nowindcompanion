package nl.myquest.nowindcompanion

import android.util.Log

// all debug output goes through here, so the 'enable debug information' setting can silence it
object Debug {
    @Volatile
    var enabled = true

    fun log(message: String) {
        if (enabled) println(message)
    }

    fun error(message: String) {
        if (enabled) Log.e("Nowind", message)
    }
}
