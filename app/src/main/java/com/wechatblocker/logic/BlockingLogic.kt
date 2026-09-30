package com.wechatblocker.logic

import android.util.Log

class BlockingLogic(
    private val minChars: Int,
    private val cooldownMinutes: Int,
    private val classKeywords: List<String>,
    private val textKeywords: List<String>,
    private val targetPackages: List<String>,
    private val enabled: Boolean
) {
    companion object {
        private const val TAG = "BlockingLogic"
    }
    
    fun shouldBlock(
        packageName: String,
        className: String?,
        visibleTexts: List<String>,
        lastPassTime: Long,
        currentTime: Long = System.currentTimeMillis()
    ): Boolean {
        Log.d(TAG, "检查是否拦截 - 包名: $packageName, 类名: $className")
        
        if (!enabled) {
            Log.d(TAG, "拦截功能已禁用")
            return false
        }
        
        // 检查是否在目标包名列表中
        if (!targetPackages.any { packageName.contains(it) }) {
            Log.d(TAG, "不在目标包名列表中")
            return false
        }
        
        // 检查冷却时间
        val timeSinceLastPass = currentTime - lastPassTime
        val cooldownMillis = cooldownMinutes * 60 * 1000L
        if (lastPassTime > 0 && timeSinceLastPass < cooldownMillis) {
            val remainingSeconds = (cooldownMillis - timeSinceLastPass) / 1000
            Log.d(TAG, "冷却中,剩余 $remainingSeconds 秒")
            return false
        }
        
        // 检测是否是视频号页面
        val isFinderPage = isFinderPage(className, visibleTexts)
        Log.d(TAG, "是否视频号页面: $isFinderPage")
        
        return isFinderPage
    }
    
    fun isFinderPage(className: String?, visibleTexts: List<String>): Boolean {
        // 方法1: 检查类名
        if (!className.isNullOrEmpty()) {
            val hasClassKeyword = classKeywords.any { keyword ->
                className.contains(keyword, ignoreCase = true)
            }
            if (hasClassKeyword) {
                Log.d(TAG, "通过类名检测到视频号: $className")
                return true
            }
        }
        
        // 方法2: 检查可见文本(后备方案)
        val matchedTexts = visibleTexts.filter { text ->
            textKeywords.any { keyword ->
                text.contains(keyword, ignoreCase = false)
            }
        }
        
        if (matchedTexts.size >= 2) {
            Log.d(TAG, "通过文本检测到视频号,匹配: $matchedTexts")
            return true
        }
        
        return false
    }
    
    fun isValidSubmission(text: String): Boolean {
        return text.length >= minChars
    }
    
    fun getRemainingChars(text: String): Int {
        return maxOf(0, minChars - text.length)
    }
}
