package nl.myquest.nowindcompanion

import android.util.Log

// all debug output goes through here, so the 'enable debug information' setting can silence it
object Debug {
    // the stored setting replaces this once it is loaded; until then follow the build type
    @Volatile
    var enabled = BuildConfig.DEBUG

    fun log(message: String) {
        if (enabled) println(message)
    }

    fun error(message: String) {
        if (enabled) Log.e("Nowind", message)
    }
}
