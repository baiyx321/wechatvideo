package com.wechatblocker.data

import java.util.Calendar

object UsageTriggerLogic {

    fun shouldShowOnOpen(lastLeftMs: Long, nowMs: Long, awayMinutes: Int): Boolean {
        if (lastLeftMs == 0L) return true
        val awayTimeMs = awayMinutes * 60 * 1000L
        return (nowMs - lastLeftMs) > awayTimeMs
    }

    fun shouldShowNightReminder(
        calendar: Calendar,
        nightHour: Int,
        nightMinute: Int,
        lastShownDate: String,
        today: String
    ): Boolean {
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)
        val nowMinutes = currentHour * 60 + currentMinute
        val triggerMinutes = nightHour * 60 + nightMinute
        if (nowMinutes < triggerMinutes) return false
        return lastShownDate != today
    }
}
