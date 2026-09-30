package com.wechatblocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.wechatblocker.data.PreferencesManager
import com.wechatblocker.logic.BlockingLogic
import com.wechatblocker.ui.BlockingOverlay

class WeChatBlockerService : AccessibilityService() {
    
    private lateinit var prefsManager: PreferencesManager
    private var overlay: BlockingOverlay? = null
    private var isCurrentlyBlocking = false
    
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
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        prefsManager = PreferencesManager(this)
        Log.d(TAG, "服务已创建")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        try {
            val packageName = event.packageName?.toString() ?: return
            val className = event.className?.toString()
            
            // 更新调试信息
            latestPackageName = packageName
            latestClassName = className
            
            // 收集可见文本
            val visibleTexts = collectVisibleTexts(event)
            latestVisibleTexts = visibleTexts
            
            Log.d(TAG, "事件 - 包名: $packageName, 类名: $className, 文本数: ${visibleTexts.size}")
            
            // 创建拦截逻辑
            val logic = BlockingLogic(
                minChars = prefsManager.minChars,
                cooldownMinutes = prefsManager.cooldownMinutes,
                classKeywords = prefsManager.classKeywords.split(",").map { it.trim() },
                textKeywords = prefsManager.textKeywords.split(",").map { it.trim() },
                targetPackages = prefsManager.targetPackages.split(",").map { it.trim() },
                enabled = prefsManager.enabled
            )
            
            // 检查是否需要拦截
            val shouldBlock = logic.shouldBlock(
                packageName = packageName,
                className = className,
                visibleTexts = visibleTexts,
                lastPassTime = prefsManager.lastPassTime
            )
            
            if (shouldBlock && !isCurrentlyBlocking) {
                showBlockingOverlay()
            } else if (!shouldBlock && isCurrentlyBlocking) {
                hideBlockingOverlay()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理事件失败", e)
        }
    }
    
    private fun collectVisibleTexts(event: AccessibilityEvent): List<String> {
        val texts = mutableListOf<String>()
        
        try {
            // 从事件中收集文本
            event.text?.forEach { charSeq ->
                charSeq?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            }
            
            // 从根节点收集文本
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
    
    private fun showBlockingOverlay() {
        Log.d(TAG, "显示拦截界面")
        isCurrentlyBlocking = true
        
        if (overlay == null) {
            overlay = BlockingOverlay(this, prefsManager) { success ->
                if (success) {
                    prefsManager.lastPassTime = System.currentTimeMillis()
                    Log.d(TAG, "用户通过验证,设置冷却时间")
                }
                hideBlockingOverlay()
                
                // 如果用户提交或返回,执行返回操作
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
        
        overlay?.show()
    }
    
    private fun hideBlockingOverlay() {
        Log.d(TAG, "隐藏拦截界面")
        isCurrentlyBlocking = false
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
        Log.d(TAG, "服务销毁")
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        hideBlockingOverlay()
        return super.onUnbind(intent)
    }
}
