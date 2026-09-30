package com.wechatblocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wechatblocker.data.AppUsageTracker
import com.wechatblocker.data.PreferencesManager
import com.wechatblocker.logic.BlockingLogic
import com.wechatblocker.ui.BlockingOverlay

class WeChatBlockerService : AccessibilityService() {
    
    private lateinit var prefsManager: PreferencesManager
    private lateinit var usageTracker: AppUsageTracker
    private var overlay: BlockingOverlay? = null
    private var isCurrentlyBlocking = false
    private var currentForegroundApp: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var nightCheckRunnable: Runnable? = null
    
    companion object {
        private const val TAG = "WeChatBlockerService"
        var instance: WeChatBlockerService? = null
            private set
        
        var latestPackageName: String? = null
            private set
        var latestClassName: String? = null
            private set
        var latestVisibleTexts: List<String> = emptyList()
            private set
        
        private val TARGET_APPS = mapOf(
            "com.tencent.mm" to "wechat",
            "com.wechatblocker.fakewechat" to "wechat",
            "com.ss.android.ugc.aweme" to "douyin",
            "com.ss.android.ugc.aweme.lite" to "douyin",
            "com.xingin.xhs" to "xiaohongshu"
        )
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        prefsManager = PreferencesManager(this)
        usageTracker = AppUsageTracker(this)
        Log.d(TAG, "服务已创建")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        try {
            val packageName = event.packageName?.toString() ?: return
            val className = event.className?.toString()
            
            latestPackageName = packageName
            latestClassName = className
            
            val visibleTexts = collectVisibleTexts(event)
            latestVisibleTexts = visibleTexts
            
            Log.d(TAG, "事件 - 包名: $packageName, 类名: $className")
            
            if (!prefsManager.enabled) {
                return
            }
            
            val appType = TARGET_APPS[packageName]
            
            // 检查是否离开了目标应用
            if (appType == null) {
                // 不是目标应用
                if (isCurrentlyBlocking) {
                    Log.d(TAG, "离开目标应用,隐藏overlay但保留pending状态")
                    overlay?.hide()
                    // 不改变isCurrentlyBlocking状态,保持pending
                }
                
                if (currentForegroundApp != null) {
                    usageTracker.setLastLeftTime(currentForegroundApp!!, System.currentTimeMillis())
                    currentForegroundApp = null
                    cancelNightCheck()
                }
                return
            }
            
            if (!isAppEnabled(appType)) {
                Log.d(TAG, "$appType is disabled in settings")
                return
            }
            
            val shouldBlockThisApp = when (appType) {
                "wechat" -> {
                    val logic = BlockingLogic(
                        minChars = prefsManager.minChars,
                        cooldownMinutes = 0,
                        classKeywords = prefsManager.classKeywords.split(",").map { it.trim() },
                        textKeywords = prefsManager.textKeywords.split(",").map { it.trim() },
                        targetPackages = emptyList(),
                        enabled = true
                    )
                    logic.isFinderPage(className, visibleTexts)
                }
                else -> true
            }
            
            // 检查是否离开了视频号页面(但仍在微信内)
            if (appType == "wechat" && !shouldBlockThisApp) {
                if (isCurrentlyBlocking) {
                    Log.d(TAG, "离开视频号页面,隐藏overlay但保留pending状态")
                    overlay?.hide()
                }
                return
            }
            
            // 现在在目标应用/页面中
            if (isCurrentlyBlocking) {
                // 有pending状态,恢复overlay
                if (overlay?.overlayView == null && overlay?.hasPendingState() == true) {
                    Log.d(TAG, "返回目标应用,恢复overlay (不计为新的on-open)")
                    overlay?.show()
                }
                return
            }
            
            // 没有pending状态,检查是否需要触发新的拦截
            if (currentForegroundApp != packageName) {
                if (currentForegroundApp != null) {
                    usageTracker.setLastLeftTime(currentForegroundApp!!, System.currentTimeMillis())
                }
                currentForegroundApp = packageName
                
                if (usageTracker.shouldShowOnOpen(packageName, prefsManager.awayMinutes)) {
                    Log.d(TAG, "显示on-open覆盖层: $packageName")
                    showBlockingOverlay(packageName)
                    usageTracker.setLastShownTime(packageName, System.currentTimeMillis())
                    usageTracker.markNightReminderShown(packageName)
                } else {
                    Log.d(TAG, "跳过on-open (最近使用): $packageName")
                }
                
                scheduleNightCheck(packageName)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理事件失败", e)
        }
    }
    
    private fun isAppEnabled(appType: String): Boolean {
        return when (appType) {
            "wechat" -> prefsManager.enableWechat
            "douyin" -> prefsManager.enableDouyin
            "xiaohongshu" -> prefsManager.enableXiaohongshu
            else -> false
        }
    }
    
    private fun scheduleNightCheck(packageName: String) {
        cancelNightCheck()
        
        nightCheckRunnable = object : Runnable {
            override fun run() {
                if (currentForegroundApp == packageName && !isCurrentlyBlocking) {
                    if (usageTracker.shouldShowNightReminder(packageName, prefsManager.nightHour)) {
                        Log.d(TAG, "显示night覆盖层: $packageName")
                        showBlockingOverlay(packageName)
                        usageTracker.markNightReminderShown(packageName)
                    }
                }
                
                handler.postDelayed(this, 60000)
            }
        }
        handler.postDelayed(nightCheckRunnable!!, 60000)
    }
    
    private fun cancelNightCheck() {
        nightCheckRunnable?.let {
            handler.removeCallbacks(it)
            nightCheckRunnable = null
        }
    }
    
    private fun collectVisibleTexts(event: AccessibilityEvent): List<String> {
        val texts = mutableListOf<String>()
        
        try {
            event.text?.forEach { charSeq ->
                charSeq?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            }
            
            rootInActiveWindow?.let { root ->
                collectTextsFromNode(root, texts)
                root.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "收集文本失败", e)
        }
        
        return texts.distinct().take(50)
    }
    
    private fun collectTextsFromNode(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        try {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collectTextsFromNode(child, texts)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "从节点收集文本失败", e)
        }
    }
    
    private fun showBlockingOverlay(packageName: String) {
        if (isCurrentlyBlocking) {
            Log.d(TAG, "覆盖层已在显示中")
            return
        }
        
        isCurrentlyBlocking = true
        
        if (overlay == null) {
            overlay = BlockingOverlay(this, prefsManager) { success ->
                if (success) {
                    usageTracker.setLastShownTime(packageName, System.currentTimeMillis())
                    Log.d(TAG, "用户通过验证")
                    // 提交成功,执行返回并清除blocking状态
                    isCurrentlyBlocking = false
                    performGlobalAction(GLOBAL_ACTION_BACK)
                } else {
                    // 点击返回按钮,执行返回并清除blocking状态
                    isCurrentlyBlocking = false
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        }
        
        overlay?.show()
    }
    
    private fun hideBlockingOverlay() {
        // 只隐藏view,不改变isCurrentlyBlocking状态
        overlay?.hide()
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "服务中断")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        hideBlockingOverlay()
        overlay?.destroy()
        overlay = null
        instance = null
        cancelNightCheck()
        Log.d(TAG, "服务销毁")
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        hideBlockingOverlay()
        return super.onUnbind(intent)
    }
}
