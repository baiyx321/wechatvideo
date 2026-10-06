package com.wechatblocker.data

import android.content.Context
import android.content.SharedPreferences

class AppUsageTracker(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("app_usage_tracker", Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFIX_LAST_SHOWN = "last_shown_"
        private const val PREFIX_LAST_LEFT = "last_left_"
        private const val PREFIX_NIGHT_SHOWN_DATE = "night_shown_date_"
    }
    
    fun getLastShownTime(packageName: String): Long {
        return prefs.getLong(PREFIX_LAST_SHOWN + packageName, 0)
    }
    
    fun setLastShownTime(packageName: String, time: Long) {
        prefs.edit().putLong(PREFIX_LAST_SHOWN + packageName, time).apply()
    }
    
    fun getLastLeftTime(packageName: String): Long {
        return prefs.getLong(PREFIX_LAST_LEFT + packageName, 0)
    }
    
    fun setLastLeftTime(packageName: String, time: Long) {
        prefs.edit().putLong(PREFIX_LAST_LEFT + packageName, time).apply()
    }
    
    fun getNightShownDate(packageName: String): String {
        return prefs.getString(PREFIX_NIGHT_SHOWN_DATE + packageName, "") ?: ""
    }
    
    fun setNightShownDate(packageName: String, date: String) {
        prefs.edit().putString(PREFIX_NIGHT_SHOWN_DATE + packageName, date).apply()
    }
    
    fun shouldShowOnOpen(packageName: String, awayMinutes: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
        return UsageTriggerLogic.shouldShowOnOpen(getLastLeftTime(packageName), nowMs, awayMinutes)
    }
    
    fun shouldShowNightReminder(
        packageName: String,
        nightHour: Int,
        nightMinute: Int = 0,
        calendar: java.util.Calendar = java.util.Calendar.getInstance()
    ): Boolean {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val today = dateFormat.format(calendar.time)
        return UsageTriggerLogic.shouldShowNightReminder(
            calendar = calendar,
            nightHour = nightHour,
            nightMinute = nightMinute,
            lastShownDate = getNightShownDate(packageName),
            today = today
        )
    }
    
    fun clearNightShownDate(packageName: String) {
        prefs.edit().remove(PREFIX_NIGHT_SHOWN_DATE + packageName).apply()
    }
    
    fun clearAllNightShownDates() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(PREFIX_NIGHT_SHOWN_DATE) }.forEach { editor.remove(it) }
        editor.apply()
    }
    
    fun markNightReminderShown(packageName: String) {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val today = dateFormat.format(java.util.Date())
        setNightShownDate(packageName, today)
    }
}
