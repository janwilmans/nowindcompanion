package nl.myquest.nowindcompanion

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import com.ftdi.j2xx.D2xxManager

// d2xx.jar breaks for apps targeting Android 14+: the first time it sees a context (D2xxManager.updateContext) it
// creates a mutable PendingIntent from an implicit Intent (IllegalArgumentException) and registers its USB permission
// receiver without an export flag (SecurityException). There is no fixed release, so do that setup here the way
// Android allows it, and fill in the context so the library skips its own setup.
// Always pass the same context to the D2xxManager calls, a different one runs the broken setup again.
object D2xxCompat {
    private const val ACTION_USB_PERMISSION = "com.ftdi.j2xx"     // D2xxManager.ACTION_USB_PERMISSION

    private var initialized = false

    @Synchronized
    fun getInstance(context: Context): D2xxManager {
        if (!initialized) {
            initialized = true
            val app = context.applicationContext
            // explicit, so it may be mutable; mutable because UsbManager adds the device and the result as extras
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(app.packageName)
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            setStatic("mContext", context)
            setStatic("mPendingIntent", PendingIntent.getBroadcast(app, 0, intent, mutable or PendingIntent.FLAG_UPDATE_CURRENT))
            setStatic("mPermissionFilter", filter)
            val receiver = getStatic("mUsbDevicePermissions") as BroadcastReceiver
            ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        return D2xxManager.getInstance(context)
    }

    private fun field(name: String) = D2xxManager::class.java.getDeclaredField(name).apply { isAccessible = true }

    private fun setStatic(name: String, value: Any) = field(name).set(null, value)

    private fun getStatic(name: String): Any? = field(name).get(null)
}
