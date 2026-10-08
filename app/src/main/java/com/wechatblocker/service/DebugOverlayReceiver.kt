package com.wechatblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wechatblocker.ui.BlockingOverlay

/**
 * 仅用于自动化测试: 把中文写入覆盖层输入框, 或触发夜间检查.
 * 提交仍由测试用 input tap 点真实按钮.
 */
class DebugOverlayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "收到 $action overlay=${BlockingOverlay.active != null} service=${WeChatBlockerService.instance != null}")
        when (action) {
            ACTION_SET_TEXT -> {
                val text = intent.getStringExtra("msg")
                    ?: intent.getStringExtra("b64")?.let { encoded ->
                        String(android.util.Base64.decode(encoded, android.util.Base64.DEFAULT), Charsets.UTF_8)
                    }
                if (text == null) {
                    Log.e(TAG, "SET_TEXT 没有 msg/b64")
                    return
                }
                Log.d(TAG, "写入覆盖层 len=${text.length}")
                BlockingOverlay.active?.setInputForTest(text)
                    ?: Log.e(TAG, "覆盖层未显示, 无法写入")
            }
            ACTION_NIGHT -> {
                val shown = WeChatBlockerService.instance?.triggerNightCheckNow() ?: false
                Log.d(TAG, "debug night trigger shown=$shown")
            }
        }
    }

    companion object {
        private const val TAG = "DebugOverlayReceiver"
        const val ACTION_SET_TEXT = "com.wechatblocker.DEBUG_SET_OVERLAY_TEXT"
        const val ACTION_NIGHT = "com.wechatblocker.DEBUG_NIGHT_TRIGGER"
    }
}
