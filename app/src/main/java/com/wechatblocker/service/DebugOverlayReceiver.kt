package com.wechatblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wechatblocker.ui.BlockingOverlay
import org.json.JSONObject
import java.io.File

/**
 * 仅用于自动化测试: 读写覆盖层、触发夜间检查。
 * 不要用 uiautomator dump 探测 overlay —— 那会 unbind AccessibilityService。
 * 提交仍由测试用 input tap 点真实按钮。
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
            ACTION_DUMP -> {
                writeDump(context)
            }
        }
    }

    private fun writeDump(context: Context) {
        val overlay = BlockingOverlay.active
        val json = try {
            val parsed = if (overlay != null) {
                JSONObject(overlay.dumpStateJson())
            } else {
                JSONObject().apply {
                    put("ts", System.currentTimeMillis())
                    put("visible", false)
                    put("hasPending", false)
                }
            }
            val service = WeChatBlockerService.instance
            if (service != null) {
                val snap = service.debugSnapshot()
                val keys = snap.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (!parsed.has(key)) parsed.put(key, snap.get(key))
                }
            } else {
                parsed.put("serviceBound", false)
            }
            parsed.toString()
        } catch (e: Exception) {
            Log.e(TAG, "组装 overlay dump 失败", e)
            JSONObject().apply {
                put("ts", System.currentTimeMillis())
                put("visible", false)
                put("error", e.message ?: "dump failed")
            }.toString()
        }
        Log.i(TAG, "OVERLAY_DUMP $json")
        val files = listOfNotNull(
            File(context.filesDir, DUMP_FILE),
            context.getExternalFilesDir(null)?.let { File(it, DUMP_FILE) }
        )
        for (f in files) {
            try {
                f.parentFile?.mkdirs()
                f.writeText(json, Charsets.UTF_8)
                Log.d(TAG, "wrote overlay dump to ${f.absolutePath} bytes=${f.length()}")
            } catch (e: Exception) {
                Log.e(TAG, "写入 ${f.absolutePath} 失败", e)
            }
        }
    }

    companion object {
        private const val TAG = "DebugOverlayReceiver"
        private const val DUMP_FILE = "overlay_state.json"
        const val ACTION_SET_TEXT = "com.wechatblocker.DEBUG_SET_OVERLAY_TEXT"
        const val ACTION_NIGHT = "com.wechatblocker.DEBUG_NIGHT_TRIGGER"
        const val ACTION_DUMP = "com.wechatblocker.DEBUG_DUMP_OVERLAY"
    }
}
