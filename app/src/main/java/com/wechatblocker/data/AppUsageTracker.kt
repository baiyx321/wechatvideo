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
    
    fun shouldShowOnOpen(packageName: String, awayMinutes: Int): Boolean {
        val now = System.currentTimeMillis()
        val lastLeft = getLastLeftTime(packageName)
        
        if (lastLeft == 0L) {
            return true
        }
        
        val awayTimeMs = awayMinutes * 60 * 1000L
        return (now - lastLeft) > awayTimeMs
    }
    
    fun shouldShowNightReminder(packageName: String, nightHour: Int): Boolean {
        val calendar = java.util.Calendar.getInstance()
        val currentHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        
        if (currentHour < nightHour) {
            return false
        }
        
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val today = dateFormat.format(java.util.Date())
        val lastShownDate = getNightShownDate(packageName)
        
        return lastShownDate != today
    }
    
    fun markNightReminderShown(packageName: String) {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val today = dateFormat.format(java.util.Date())
        setNightShownDate(packageName, today)
    }
}
