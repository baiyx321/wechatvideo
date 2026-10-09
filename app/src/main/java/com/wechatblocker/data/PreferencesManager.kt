package com.wechatblocker.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = 
        context.getSharedPreferences("wechat_blocker_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_MIN_CHARS = "min_chars"
        private const val KEY_COOLDOWN_MINUTES = "cooldown_minutes"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_PROMPTS = "prompts"
        private const val KEY_CLASS_KEYWORDS = "class_keywords"
        private const val KEY_TEXT_KEYWORDS = "text_keywords"
        private const val KEY_TARGET_PACKAGES = "target_packages"
        private const val KEY_LAST_PASS_TIME = "last_pass_time"
        private const val KEY_COPY_TYPING_MODE = "copy_typing_mode"
        private const val KEY_ENABLED_BOOKS = "enabled_books"
        private const val KEY_CUSTOM_TEXTS = "custom_texts"
        private const val KEY_AWAY_MINUTES = "away_minutes"
        private const val KEY_NIGHT_HOUR = "night_hour"
        private const val KEY_NIGHT_MINUTE = "night_minute"
        private const val KEY_ENABLE_WECHAT = "enable_wechat"
        private const val KEY_ENABLE_DOUYIN = "enable_douyin"
        private const val KEY_ENABLE_XIAOHONGSHU = "enable_xiaohongshu"
        
        const val DEFAULT_MIN_CHARS = 50
        const val DEFAULT_COOLDOWN = 10
        const val DEFAULT_PROMPTS = "我为什么要刷视频?\n我现在本该做什么?"
        const val DEFAULT_CLASS_KEYWORDS = "finder,FinderHomeUI,FinderUI"
        const val DEFAULT_TEXT_KEYWORDS = "视频号,关注,朋友,推荐"
        const val DEFAULT_TARGET_PACKAGES = "com.tencent.mm,com.wechatblocker.fakewechat"
        const val DEFAULT_AWAY_MINUTES = 5
        const val DEFAULT_NIGHT_HOUR = 23
        const val DEFAULT_NIGHT_MINUTE = 0
    }
    
    var minChars: Int
        get() = prefs.getInt(KEY_MIN_CHARS, DEFAULT_MIN_CHARS)
        set(value) = prefs.edit().putInt(KEY_MIN_CHARS, value).apply()
    
    var cooldownMinutes: Int
        get() = prefs.getInt(KEY_COOLDOWN_MINUTES, DEFAULT_COOLDOWN)
        set(value) = prefs.edit().putInt(KEY_COOLDOWN_MINUTES, value).apply()
    
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()
    
    var prompts: String
        get() = prefs.getString(KEY_PROMPTS, DEFAULT_PROMPTS) ?: DEFAULT_PROMPTS
        set(value) = prefs.edit().putString(KEY_PROMPTS, value).apply()
    
    var classKeywords: String
        get() = prefs.getString(KEY_CLASS_KEYWORDS, DEFAULT_CLASS_KEYWORDS) ?: DEFAULT_CLASS_KEYWORDS
        set(value) = prefs.edit().putString(KEY_CLASS_KEYWORDS, value).apply()
    
    var textKeywords: String
        get() = prefs.getString(KEY_TEXT_KEYWORDS, DEFAULT_TEXT_KEYWORDS) ?: DEFAULT_TEXT_KEYWORDS
        set(value) = prefs.edit().putString(KEY_TEXT_KEYWORDS, value).apply()
    
    var targetPackages: String
        get() = prefs.getString(KEY_TARGET_PACKAGES, DEFAULT_TARGET_PACKAGES) ?: DEFAULT_TARGET_PACKAGES
        set(value) = prefs.edit().putString(KEY_TARGET_PACKAGES, value).apply()
    
    var lastPassTime: Long
        get() = prefs.getLong(KEY_LAST_PASS_TIME, 0)
        set(value) = prefs.edit().putLong(KEY_LAST_PASS_TIME, value).apply()
    
    var copyTypingMode: Boolean
        get() = prefs.getBoolean(KEY_COPY_TYPING_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_COPY_TYPING_MODE, value).apply()
    
    var enabledBooks: Set<String>
        get() {
            val saved = prefs.getStringSet(KEY_ENABLED_BOOKS, null)
            return saved ?: TextLibraryManager.DEFAULT_BOOKS.toSet()
        }
        set(value) = prefs.edit().putStringSet(KEY_ENABLED_BOOKS, value).apply()
    
    var customTexts: String
        get() = prefs.getString(KEY_CUSTOM_TEXTS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_TEXTS, value).apply()
    
    var awayMinutes: Int
        get() = prefs.getInt(KEY_AWAY_MINUTES, DEFAULT_AWAY_MINUTES)
        set(value) = prefs.edit().putInt(KEY_AWAY_MINUTES, value).apply()
    
    var nightHour: Int
        get() = prefs.getInt(KEY_NIGHT_HOUR, DEFAULT_NIGHT_HOUR)
        set(value) = prefs.edit().putInt(KEY_NIGHT_HOUR, value).apply()
    
    var nightMinute: Int
        get() = prefs.getInt(KEY_NIGHT_MINUTE, DEFAULT_NIGHT_MINUTE)
        set(value) = prefs.edit().putInt(KEY_NIGHT_MINUTE, value).apply()
    
    fun nightTimeLabel(): String {
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", nightHour, nightMinute)
    }
    
    var enableWechat: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_WECHAT, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_WECHAT, value).apply()
    
    var enableDouyin: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_DOUYIN, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_DOUYIN, value).apply()
    
    var enableXiaohongshu: Boolean
        get() = prefs.getBoolean(KEY_ENABLE_XIAOHONGSHU, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLE_XIAOHONGSHU, value).apply()
    
    fun getRandomPrompt(): String {
        val promptsList = prompts.split("\n---\n")
        return promptsList.random()
    }
}
