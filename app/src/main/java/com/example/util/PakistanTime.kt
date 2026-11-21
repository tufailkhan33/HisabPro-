package com.example.util

import com.example.shared.util.SharedPakistanTime
import java.util.TimeZone

object PakistanTime {
    val timeZone: TimeZone = SharedPakistanTime.timeZone

    fun nowMillis(): Long = SharedPakistanTime.nowMillis()
    fun formatDate(millis: Long = nowMillis()): String = SharedPakistanTime.formatDate(millis)
    fun formatTime(millis: Long = nowMillis()): String = SharedPakistanTime.formatTime(millis)
    fun formatNumericDate(millis: Long = nowMillis()): String = SharedPakistanTime.formatNumericDate(millis)
    fun formatDashDate(millis: Long = nowMillis()): String = SharedPakistanTime.formatDashDate(millis)
    fun formatDateTime(millis: Long = nowMillis()): String = SharedPakistanTime.formatDateTime(millis)
    fun formatHeaderClock(millis: Long = nowMillis()): String = SharedPakistanTime.formatHeaderClock(millis)
    fun startOfDay(millis: Long): Long = SharedPakistanTime.startOfDay(millis)
    fun endOfDay(millis: Long): Long = SharedPakistanTime.endOfDay(millis)
    fun dayRangeFor(millis: Long): Pair<Long, Long> = SharedPakistanTime.dayRangeFor(millis)
    fun weekRangeFor(millis: Long): Pair<Long, Long> = SharedPakistanTime.weekRangeFor(millis)
    fun monthRangeFor(millis: Long): Pair<Long, Long> = SharedPakistanTime.monthRangeFor(millis)
    fun todayRange(): Pair<Long, Long> = SharedPakistanTime.todayRange()
    fun yesterdayRange(): Pair<Long, Long> = SharedPakistanTime.yesterdayRange()
    fun lastDaysRange(days: Int): Pair<Long, Long> = SharedPakistanTime.lastDaysRange(days)
    fun thisWeekRange(): Pair<Long, Long> = SharedPakistanTime.thisWeekRange()
    fun previousWeekRange(): Pair<Long, Long> = SharedPakistanTime.previousWeekRange()
    fun thisMonthRange(): Pair<Long, Long> = SharedPakistanTime.thisMonthRange()
    fun previousMonthRange(): Pair<Long, Long> = SharedPakistanTime.previousMonthRange()
    fun isSamePakistanDay(firstMillis: Long, secondMillis: Long): Boolean =
        SharedPakistanTime.isSamePakistanDay(firstMillis, secondMillis)
    fun isFuturePakistanDay(txMillis: Long, referenceMillis: Long = nowMillis()): Boolean =
        SharedPakistanTime.isFuturePakistanDay(txMillis, referenceMillis)
    fun isBackdatedPakistanDay(txMillis: Long, referenceMillis: Long = nowMillis()): Boolean =
        SharedPakistanTime.isBackdatedPakistanDay(txMillis, referenceMillis)
    fun formatShortDateTime(millis: Long): String = SharedPakistanTime.formatShortDateTime(millis)
    fun toDatePickerUtcMillis(pktMillis: Long): Long = SharedPakistanTime.toDatePickerUtcMillis(pktMillis)
    fun fromDatePickerUtcMillis(utcMidnightMillis: Long, preserveTimeFromMillis: Long? = null): Long =
        SharedPakistanTime.fromDatePickerUtcMillis(utcMidnightMillis, preserveTimeFromMillis)
}
