package com.wechatblocker

import android.app.UiAutomation
import android.util.Log
import androidx.test.runner.AndroidJUnitRunner

/**
 * UiAutomator 默认会注销其它无障碍服务。本应用的拦截完全依赖
 * WeChatBlockerService，测试时必须带上 FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES。
 */
class OverlayTestRunner : AndroidJUnitRunner() {

    override fun getUiAutomation(): UiAutomation {
        Log.i(TAG, "getUiAutomation() with FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES")
        return getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    }

    override fun getUiAutomation(flags: Int): UiAutomation {
        val merged = flags or UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        Log.i(TAG, "getUiAutomation(flags=$flags) merged=$merged")
        return super.getUiAutomation(merged)
    }

    companion object {
        private const val TAG = "OverlayTestRunner"
    }
}
