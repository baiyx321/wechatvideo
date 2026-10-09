package com.wechatblocker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EndToEndTest {
    
    private lateinit var device: UiDevice
    
    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        device.waitForIdle(2000)
    }
    
    @Test
    fun testAppLaunches() {
        // 启动应用
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage("com.wechatblocker")
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        
        // 等待应用启动
        device.wait(Until.hasObject(By.pkg("com.wechatblocker").depth(0)), 5000)
        
        // 验证应用已启动
        val appPackage = device.currentPackageName
        assertEquals("com.wechatblocker", appPackage)
    }
    
    @Test
    fun testMainScreenElements() {
        // 启动应用
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage("com.wechatblocker")
        intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        
        device.wait(Until.hasObject(By.pkg("com.wechatblocker").depth(0)), 5000)
        
        val onboarding = device.findObject(By.res("com.wechatblocker:id/openAccessibilityButton"))
        val statusText = device.findObject(By.res("com.wechatblocker:id/statusText"))
        assertTrue(
            "应看到引导页或主界面状态",
            onboarding != null || statusText != null
        )
    }
}
