package nl.myquest.nowindcompanion

import android.app.ActivityManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.LinkedList

// typealias TypeUnderTest = UByte // Average memory increase after allocation: 4373 KB
// typealias TypeUnderTest = Int   // Average memory increase after allocation: 3991 KB
// typealias TypeUnderTest = Long //  Average memory increase after allocation: 3834 KB

typealias TypeUnderTest = Double

var fillValue: TypeUnderTest = 5.0

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    private fun getAvailableMemory(context: Context): Long {
        val memInfo = ActivityManager.MemoryInfo()

        // Cast the result of getSystemService to ActivityManager
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.getMemoryInfo(
            memInfo
        )
        return memInfo.availMem / 1024 // return value in KB
    }

    fun calculateAverage(measurements: LinkedList<Long>): Long {
        if (measurements.isEmpty()) {
            return 0
        }
        return measurements.sum() / measurements.size
    }

    @Test
    fun useAppContext() {

        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val keeper = LinkedList<MutableList<TypeUnderTest>>();
        val proto = List<TypeUnderTest>(1 * 1024 * 1024) { fillValue }

        var availableMemory = getAvailableMemory(appContext);
        val samples = 10
        val measurements = LinkedList<Long>()
        for (i in 1..samples) {

            val test = proto.toMutableList()
            test.fill(fillValue)
            fillValue += 1
            keeper.add(test)
            val stillAvailable = getAvailableMemory(appContext)
            measurements.add(availableMemory - stillAvailable)
            availableMemory = stillAvailable
        }

        val average = calculateAverage(measurements)
        println("Average memory increase after allocation: $average KB")

        // Average memory increase after allocation: 4463 KB for LinkedList<MutableList<UByte>>();

        println("Size of Byte in bytes: ${Byte.SIZE_BYTES}")
        println("Size of UByte in bytes: ${UByte.SIZE_BYTES}")
        println("Size of Int in bytes: ${Int.SIZE_BYTES}")
        println("Size of UInt in bytes: ${UInt.SIZE_BYTES}")
        println("Size of Long in bytes: ${Long.SIZE_BYTES}")
        println("Size of Double in bytes: ${Double.SIZE_BYTES}")

        assertEquals("nl.myquest.nowindcompanion", appContext.packageName)
    }
}

