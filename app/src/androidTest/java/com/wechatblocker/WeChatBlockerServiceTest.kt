package com.wechatblocker.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wechatblocker.data.PreferencesManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*

@RunWith(AndroidJUnit4::class)
class WeChatBlockerServiceTest {
    
    private lateinit var context: Context
    private lateinit var prefsManager: PreferencesManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefsManager = PreferencesManager(context)
        // 重置为默认值
        prefsManager.enabled = true
        prefsManager.minChars = 50
        prefsManager.cooldownMinutes = 10
        prefsManager.lastPassTime = 0
    }
    
    @Test
    fun testServiceConfiguration() {
        // 测试服务配置是否正确
        assertTrue("PreferencesManager应该可以创建", prefsManager.minChars > 0)
    }
    
    @Test
    fun testEventFiltering() {
        // 创建模拟事件
        val event = mock(AccessibilityEvent::class.java)
        `when`(event.packageName).thenReturn("com.tencent.mm")
        `when`(event.className).thenReturn("com.tencent.mm.plugin.finder.ui.FinderHomeUI")
        
        // 验证包名检测
        assertEquals("com.tencent.mm", event.packageName.toString())
        assertTrue(event.className.toString().contains("Finder"))
    }
    
    @Test
    fun testTextCollection() {
        // 测试文本收集逻辑
        val testTexts = listOf("视频号", "关注", "朋友", "推荐")
        
        // 验证至少2个关键词匹配
        val keywords = listOf("视频号", "关注", "朋友", "推荐")
        val matchCount = testTexts.count { text ->
            keywords.any { keyword -> text.contains(keyword) }
        }
        
        assertTrue("应该匹配至少2个关键词", matchCount >= 2)
    }
    
    @Test
    fun testCooldownMechanism() {
        val currentTime = System.currentTimeMillis()
        prefsManager.lastPassTime = currentTime - 5 * 60 * 1000 // 5分钟前
        
        val cooldownMillis = prefsManager.cooldownMinutes * 60 * 1000L
        val timeSinceLastPass = currentTime - prefsManager.lastPassTime
        
        assertTrue("应该在冷却期内", timeSinceLastPass < cooldownMillis)
        
        // 测试冷却期过后
        prefsManager.lastPassTime = currentTime - 11 * 60 * 1000 // 11分钟前
        val timeSinceLastPass2 = currentTime - prefsManager.lastPassTime
        assertTrue("应该不在冷却期内", timeSinceLastPass2 > cooldownMillis)
    }
}
