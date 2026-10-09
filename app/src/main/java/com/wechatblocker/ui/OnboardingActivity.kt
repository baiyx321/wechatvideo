package com.wechatblocker.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.wechatblocker.R
import com.wechatblocker.util.AccessibilitySettingsLauncher
import org.json.JSONObject

/**
 * 首次打开或无障碍未开启时的引导。不能替用户打开开关，只能跳到系统设置。
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var openButton: Button
    private lateinit var retryHint: TextView
    private var openedSettingsOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (AccessibilitySettingsLauncher.isServiceEnabled(this)) {
            continueToApp()
            return
        }
        setContentView(R.layout.activity_onboarding)
        openButton = findViewById(R.id.openAccessibilityButton)
        retryHint = findViewById(R.id.onboardingRetryHint)
        openButton.setOnClickListener {
            openedSettingsOnce = true
            Log.d(TAG, "用户点击去系统设置")
            AccessibilitySettingsLauncher.open(this)
        }
    }

    override fun onStart() {
        super.onStart()
        instance = this
    }

    override fun onStop() {
        if (instance === this) instance = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (AccessibilitySettingsLauncher.isServiceEnabled(this)) {
            Log.d(TAG, "返回时服务已启用，进入主界面")
            continueToApp()
            return
        }
        if (!::openButton.isInitialized) return
        if (openedSettingsOnce) {
            retryHint.visibility = View.VISIBLE
            openButton.setText(R.string.onboarding_retry_button)
            Log.d(TAG, "返回时仍未启用，显示重试")
        }
    }

    private fun continueToApp() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    fun dumpStateJson(): String {
        val json = JSONObject()
        json.put("ts", System.currentTimeMillis())
        json.put("visible", ::openButton.isInitialized && !isFinishing)
        json.put("serviceEnabled", AccessibilitySettingsLauncher.isServiceEnabled(this))
        json.put("openedSettingsOnce", openedSettingsOnce)
        if (::openButton.isInitialized) {
            json.put("retryVisible", retryHint.visibility == View.VISIBLE)
            json.put("buttonText", openButton.text.toString())
            json.put("title", findViewById<TextView>(R.id.onboardingTitle)?.text?.toString() ?: "")
            json.put("explanation", findViewById<TextView>(R.id.onboardingExplanation)?.text?.toString() ?: "")
            json.put("openCenter", viewCenter(openButton))
        }
        return json.toString()
    }

    private fun viewCenter(view: View): JSONObject {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return JSONObject().apply {
            put("x", loc[0] + view.width / 2)
            put("y", loc[1] + view.height / 2)
            put("w", view.width)
            put("h", view.height)
        }
    }

    companion object {
        private const val TAG = "OnboardingActivity"
        @Volatile
        var instance: OnboardingActivity? = null
            private set
    }
}
