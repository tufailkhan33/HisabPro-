package com.example.shared.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SharedPakistanTime {
    val timeZone: TimeZone = TimeZone.getTimeZone("Asia/Karachi")
    private val locale = Locale.ENGLISH
    private val utcTimeZone: TimeZone = TimeZone.getTimeZone("UTC")

    fun nowMillis(): Long = System.currentTimeMillis()

    fun formatDate(millis: Long = nowMillis()): String {
        val sdf = SimpleDateFormat("dd MMM yyyy", locale)
        sdf.timeZone = timeZone
        return sdf.format(Date(millis))
    }

    fun formatTime(millis: Long = nowMillis()): String {
        val sdf = SimpleDateFormat("hh:mm a", locale)
        sdf.timeZone = timeZone
        return sdf.format(Date(millis))
    }

    fun formatNumericDate(millis: Long = nowMillis()): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", locale)
        sdf.timeZone = timeZone
        return sdf.format(Date(millis))
    }

    fun formatDashDate(millis: Long = nowMillis()): String {
        val sdf = SimpleDateFormat("dd-MM-yyyy", locale)
        sdf.timeZone = timeZone
        return sdf.format(Date(millis))
    }

    fun formatDateTime(millis: Long = nowMillis()): String {
        val sdf = SimpleDateFormat("dd MMM yyyy • hh:mm a 'PKT'", locale)
        sdf.timeZone = timeZone
        return sdf.format(Date(millis))
    }

    fun formatHeaderClock(millis: Long = nowMillis()): String {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy • hh:mm:ss a 'PKT'", locale)
        sdf.timeZone = timeZone
        return sdf.format(Date(millis))
    }

    private fun calendarAt(millis: Long = nowMillis()): Calendar {
        return Calendar.getInstance(timeZone, locale).apply {
            timeInMillis = millis
        }
    }

    fun startOfDay(millis: Long): Long {
        val cal = calendarAt(millis)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun endOfDay(millis: Long): Long {
        val cal = calendarAt(millis)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    fun dayRangeFor(millis: Long): Pair<Long, Long> = startOfDay(millis) to endOfDay(millis)

    fun weekRangeFor(millis: Long): Pair<Long, Long> {
        val cal = calendarAt(millis)
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val start = startOfDay(cal.timeInMillis)
        cal.add(Calendar.DAY_OF_YEAR, 6)
        val end = endOfDay(cal.timeInMillis)
        return start to end
    }

    fun monthRangeFor(millis: Long): Pair<Long, Long> {
        val cal = calendarAt(millis)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = startOfDay(cal.timeInMillis)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = endOfDay(cal.timeInMillis)
        return start to end
    }

    fun todayRange(): Pair<Long, Long> {
        val now = nowMillis()
        return startOfDay(now) to endOfDay(now)
    }

    fun yesterdayRange(): Pair<Long, Long> {
        val cal = calendarAt()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val ts = cal.timeInMillis
        return startOfDay(ts) to endOfDay(ts)
    }

    fun lastDaysRange(days: Int): Pair<Long, Long> {
        val now = nowMillis()
        val cal = calendarAt(now)
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1))
        return startOfDay(cal.timeInMillis) to endOfDay(now)
    }

    fun thisWeekRange(): Pair<Long, Long> {
        val now = nowMillis()
        val cal = calendarAt(now)
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        return startOfDay(cal.timeInMillis) to endOfDay(now)
    }

    fun previousWeekRange(): Pair<Long, Long> {
        val (thisWeekStart, _) = thisWeekRange()
        val cal = calendarAt(thisWeekStart)
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val prevStart = startOfDay(cal.timeInMillis)
        cal.add(Calendar.DAY_OF_YEAR, 6)
        val prevEnd = endOfDay(cal.timeInMillis)
        return prevStart to prevEnd
    }

    fun thisMonthRange(): Pair<Long, Long> {
        val now = nowMillis()
        val cal = calendarAt(now)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return startOfDay(cal.timeInMillis) to endOfDay(now)
    }

    fun previousMonthRange(): Pair<Long, Long> {
        val cal = calendarAt()
        cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = startOfDay(cal.timeInMillis)
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = endOfDay(cal.timeInMillis)
        return start to end
    }

    fun isSamePakistanDay(firstMillis: Long, secondMillis: Long): Boolean {
        val c1 = calendarAt(firstMillis)
        val c2 = calendarAt(secondMillis)
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }

    fun isFuturePakistanDay(txMillis: Long, referenceMillis: Long = nowMillis()): Boolean {
        return txMillis > endOfDay(referenceMillis)
    }

    fun isBackdatedPakistanDay(txMillis: Long, referenceMillis: Long = nowMillis()): Boolean {
        return txMillis < startOfDay(referenceMillis)
    }

    fun formatShortDateTime(millis: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy hh:mm a", locale)
        sdf.timeZone = timeZone
        return sdf.format(Date(millis))
    }

    fun toDatePickerUtcMillis(pktMillis: Long): Long {
        val pktCal = calendarAt(pktMillis)
        return Calendar.getInstance(utcTimeZone, locale).apply {
            clear()
            set(
                pktCal.get(Calendar.YEAR),
                pktCal.get(Calendar.MONTH),
                pktCal.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun fromDatePickerUtcMillis(utcMidnightMillis: Long, preserveTimeFromMillis: Long? = null): Long {
        val utcCal = Calendar.getInstance(utcTimeZone, locale).apply {
            timeInMillis = utcMidnightMillis
        }
        val hour: Int
        val minute: Int
        val second: Int
        if (preserveTimeFromMillis != null) {
            val timeCal = calendarAt(preserveTimeFromMillis)
            hour = timeCal.get(Calendar.HOUR_OF_DAY)
            minute = timeCal.get(Calendar.MINUTE)
            second = timeCal.get(Calendar.SECOND)
        } else {
            hour = 12
            minute = 0
            second = 0
        }
        return Calendar.getInstance(timeZone, locale).apply {
            clear()
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                hour,
                minute,
                second
            )
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun parseDashDate(dateText: String): Long? {
        return try {
            val sdf = SimpleDateFormat("dd-MM-yyyy", locale).apply {
                timeZone = this@SharedPakistanTime.timeZone
                isLenient = false
            }
            val parsed = sdf.parse(dateText.trim()) ?: return null
            fromDatePickerUtcMillis(toDatePickerUtcMillis(parsed.time))
        } catch (_: Exception) {
            null
        }
    }
}
