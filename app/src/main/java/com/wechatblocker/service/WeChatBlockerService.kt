package com.wechatblocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wechatblocker.data.AppUsageTracker
import com.wechatblocker.data.Passage
import com.wechatblocker.data.PreferencesManager
import com.wechatblocker.data.TextLibraryManager
import com.wechatblocker.logic.BlockingLogic
import com.wechatblocker.ui.BlockingOverlay
import org.json.JSONObject
import java.util.concurrent.Executors

class WeChatBlockerService : AccessibilityService() {

    private lateinit var prefsManager: PreferencesManager
    private lateinit var usageTracker: AppUsageTracker
    private var overlay: BlockingOverlay? = null
    private var isCurrentlyBlocking = false
    private var currentForegroundApp: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var nightCheckRunnable: Runnable? = null
    private var forceNightPending = false
    private val ioExecutor = Executors.newSingleThreadExecutor()
    @Volatile
    private var preloadedPassages: List<Passage> = emptyList()

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
            "com.ss.android.ugc.aweme.test" to "douyin",
            "com.xingin.xhs" to "xiaohongshu",
            "com.xingin.xhs.test" to "xiaohongshu"
        )

        private val OWN_UI_CLASSES = listOf(
            "MainActivity",
            "OnboardingActivity",
            "SettingsActivity",
            "DebugActivity",
            "HistoryActivity"
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefsManager = PreferencesManager(this)
        usageTracker = AppUsageTracker(this)
        Log.d(TAG, "服务已创建")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "服务已连接, 开始预加载段落")
        preloadPassagesAsync()
    }

    private fun preloadPassagesAsync() {
        ioExecutor.execute {
            try {
                val start = System.currentTimeMillis()
                val passages = TextLibraryManager(this).extractPassages(
                    enabledBooks = prefsManager.enabledBooks,
                    minLength = prefsManager.minChars,
                    maxLength = 80,
                    customText = prefsManager.customTexts,
                    maxPassages = 80
                )
                preloadedPassages = passages
                Log.d(TAG, "预加载段落完成: ${passages.size} 段, 耗时 ${System.currentTimeMillis() - start}ms")
                handler.post {
                    overlay?.setPreloadedPassages(passages)
                }
            } catch (e: Exception) {
                Log.e(TAG, "预加载段落失败", e)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        try {
            val packageName = event.packageName?.toString() ?: return
            val className = event.className?.toString()
            val eventType = event.eventType

            latestPackageName = packageName
            latestClassName = className

            if (!prefsManager.enabled) {
                return
            }

            if (isNoiseEvent(packageName, className)) {
                Log.d(TAG, "忽略噪声事件 pkg=$packageName class=$className type=$eventType")
                return
            }

            // 只用窗口状态变化判断离开/返回。WINDOWS_CHANGED 在 addView 时也会冒泡,容易误伤 overlay。
            val isWindowChange = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

            val appType = TARGET_APPS[packageName]
            if (isWindowChange) {
                Log.d(
                    TAG,
                    "窗口变化 pkg=$packageName class=$className appType=$appType blocking=$isCurrentlyBlocking fg=$currentForegroundApp"
                )
            }

            if (appType == null) {
                if (!isWindowChange) {
                    return
                }
                Log.d(TAG, "离开目标应用, pkg=$packageName class=$className")
                if (isCurrentlyBlocking) {
                    Log.d(TAG, "隐藏overlay但保留pending状态")
                    overlay?.hide()
                }
                if (currentForegroundApp != null) {
                    usageTracker.setLastLeftTime(currentForegroundApp!!, System.currentTimeMillis())
                    Log.d(TAG, "记录离开时间: $currentForegroundApp")
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
                    val visibleTexts = collectVisibleTexts(event)
                    latestVisibleTexts = visibleTexts
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

            if (appType == "wechat" && !shouldBlockThisApp) {
                if (isCurrentlyBlocking) {
                    Log.d(TAG, "离开视频号页面,隐藏overlay但保留pending状态")
                    overlay?.hide()
                }
                return
            }

            if (isCurrentlyBlocking) {
                if (overlay?.overlayView == null && overlay?.hasPendingState() == true) {
                    Log.d(TAG, "返回目标应用,恢复overlay (不计为新的on-open) pkg=$packageName")
                    showOverlayView()
                }
                currentForegroundApp = packageName
                return
            }

            if (currentForegroundApp != packageName) {
                if (currentForegroundApp != null) {
                    usageTracker.setLastLeftTime(currentForegroundApp!!, System.currentTimeMillis())
                }
                currentForegroundApp = packageName

                if (forceNightPending || usageTracker.shouldShowNightReminder(
                        packageName,
                        prefsManager.nightHour,
                        prefsManager.nightMinute
                    )
                ) {
                    Log.d(TAG, "显示night覆盖层: $packageName force=$forceNightPending")
                    showBlockingOverlay(packageName)
                    usageTracker.markNightReminderShown(packageName)
                    forceNightPending = false
                } else if (usageTracker.shouldShowOnOpen(packageName, prefsManager.awayMinutes)) {
                    Log.d(TAG, "显示on-open覆盖层: $packageName")
                    showBlockingOverlay(packageName)
                    usageTracker.setLastShownTime(packageName, System.currentTimeMillis())
                    usageTracker.markNightReminderShown(packageName)
                } else {
                    Log.d(TAG, "跳过on-open (最近使用): $packageName left=${usageTracker.getLastLeftTime(packageName)} away=${prefsManager.awayMinutes}")
                }

                scheduleNightCheck(packageName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理事件失败", e)
        }
    }

    private fun isNoiseEvent(packageName: String, className: String?): Boolean {
        if (packageName == "com.android.systemui") return true
        if (packageName.contains("inputmethod", ignoreCase = true)) return true
        if (packageName.contains("keyboard", ignoreCase = true)) return true
        if (packageName == "com.android.adbkeyboard") return true
        if (packageName.endsWith(".permissioncontroller")) return true
        if (packageName == applicationContext.packageName) {
            val isOwnActivity = OWN_UI_CLASSES.any { className?.contains(it) == true }
            if (isOwnActivity) {
                Log.d(TAG, "自身界面 $className, 按离开目标处理")
                return false
            }
            return true
        }
        return false
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
                    if (forceNightPending || usageTracker.shouldShowNightReminder(
                            packageName,
                            prefsManager.nightHour,
                            prefsManager.nightMinute
                        )
                    ) {
                        Log.d(TAG, "定时显示night覆盖层: $packageName")
                        showBlockingOverlay(packageName)
                        usageTracker.markNightReminderShown(packageName)
                        forceNightPending = false
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
                collectTextsFromNode(root, texts, 0)
                root.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "收集文本失败", e)
        }
        return texts.distinct().take(50)
    }

    private fun collectTextsFromNode(node: AccessibilityNodeInfo, texts: MutableList<String>, depth: Int) {
        if (depth > 12 || texts.size >= 50) return
        try {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { child ->
                    collectTextsFromNode(child, texts, depth + 1)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "从节点收集文本失败", e)
        }
    }

    private fun ensureOverlay(packageName: String) {
        if (overlay != null) return
        overlay = BlockingOverlay(this, prefsManager) { success ->
            val pkg = currentForegroundApp ?: packageName
            if (success) {
                usageTracker.setLastShownTime(pkg, System.currentTimeMillis())
                Log.d(TAG, "用户通过验证 pkg=$pkg")
            } else {
                Log.d(TAG, "用户点击返回 pkg=$pkg")
            }
            isCurrentlyBlocking = false
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        overlay?.setPreloadedPassages(preloadedPassages)
        Log.d(TAG, "创建overlay, 预加载段落=${preloadedPassages.size}")
    }

    private fun showOverlayView() {
        overlay?.show()
    }

    private fun showBlockingOverlay(packageName: String) {
        if (isCurrentlyBlocking) {
            Log.d(TAG, "覆盖层已在显示中")
            return
        }

        isCurrentlyBlocking = true
        ensureOverlay(packageName)
        showOverlayView()
    }

    fun shortenAwayForDebug() {
        prefsManager.awayMinutes = 0
        Log.d(TAG, "debug: awayMinutes shortened to 0")
    }

    fun triggerNightCheckNow(): Boolean {
        usageTracker.clearAllNightShownDates()
        forceNightPending = true
        Log.d(TAG, "debug: night trigger armed, foreground=$currentForegroundApp blocking=$isCurrentlyBlocking")
        val pkg = currentForegroundApp
        if (pkg != null && !isCurrentlyBlocking) {
            showBlockingOverlay(pkg)
            usageTracker.markNightReminderShown(pkg)
            forceNightPending = false
            return true
        }
        return false
    }

    fun debugSnapshot(): JSONObject {
        return JSONObject().apply {
            put("serviceBound", true)
            put("blocking", isCurrentlyBlocking)
            put("foreground", currentForegroundApp ?: "")
            put("enabled", prefsManager.enabled)
            put("enableDouyin", prefsManager.enableDouyin)
            put("enableXhs", prefsManager.enableXiaohongshu)
            put("awayMinutes", prefsManager.awayMinutes)
            put("preloadCount", preloadedPassages.size)
            put("forceNightPending", forceNightPending)
        }
    }

    private fun hideBlockingOverlay() {
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
        ioExecutor.shutdownNow()
        Log.d(TAG, "服务销毁")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "服务unbound, 销毁overlay以免BadTokenException")
        hideBlockingOverlay()
        overlay?.destroy()
        overlay = null
        isCurrentlyBlocking = false
        currentForegroundApp = null
        return super.onUnbind(intent)
    }
}
