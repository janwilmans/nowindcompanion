package nl.myquest.nowindcompanion

import org.junit.Assert.assertEquals
import org.junit.Test

class SkipDiskTest {
    @Test
    fun one_boot_without_disk_then_the_disk_is_back() {
        var skip = SkipDisk.NextBoot
        skip = skip.atBoot()
        assertEquals(SkipDisk.ThisBoot, skip)
        skip = skip.atBoot()
        assertEquals(SkipDisk.Off, skip)
        assertEquals(SkipDisk.Off, skip.atBoot())
    }
}
