package com.example

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.example.util.PakistanTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PakistanTimeAndResourcesTest {

    @Test
    fun `datePickerUtcRoundTrip preserves PKT calendar date even between 00 and 05 AM`() {
        // 2026-11-17 01:30:45 PKT == 2026-11-16 20:30:45 UTC
        val pktCal = Calendar.getInstance(PakistanTime.timeZone).apply {
            clear()
            set(2026, Calendar.NOVEMBER, 17, 1, 30, 45)
            set(Calendar.MILLISECOND, 0)
        }
        val originalPktMillis = pktCal.timeInMillis

        val pickerUtcMillis = PakistanTime.toDatePickerUtcMillis(originalPktMillis)
        val restoredPktMillis = PakistanTime.fromDatePickerUtcMillis(
            utcMidnightMillis = pickerUtcMillis,
            preserveTimeFromMillis = originalPktMillis
        )

        assertEquals("17/11/2026", PakistanTime.formatNumericDate(restoredPktMillis))
        assertEquals("01:30 AM", PakistanTime.formatTime(restoredPktMillis))
    }

    @Test
    fun `custom adaptive launcher icon and brand logos resolve cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher))
        assertNotNull(ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_background))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.hisabpro_logo))
        assertNotNull(ContextCompat.getDrawable(context, R.drawable.hisabpro_logo_rounded))
    }
}
