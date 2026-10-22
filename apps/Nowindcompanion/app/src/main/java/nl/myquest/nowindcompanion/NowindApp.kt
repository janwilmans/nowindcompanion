package nl.myquest.nowindcompanion

import android.app.Application
import android.content.Context

// owns everything that must live as long as the process, independent of activity re-creation
class NowindApp : Application() {
    val state = HostState()
    lateinit var client: FTDIClient
        private set

    override fun onCreate() {
        super.onCreate()
        client = FTDIClient(this, state)
        client.start()
    }
}

val Context.nowindApp: NowindApp
    get() = applicationContext as NowindApp
