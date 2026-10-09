package com.wechatblocker.ui

import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityManager
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
    private var continued = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            maybeContinue("settings")
        }
    }

    private val a11yListener = AccessibilityManager.AccessibilityStateChangeListener {
        maybeContinue("a11y-state")
    }

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
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            settingsObserver
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED),
            false,
            settingsObserver
        )
        accessibilityManager()?.addAccessibilityStateChangeListener(a11yListener)
    }

    override fun onStop() {
        try {
            contentResolver.unregisterContentObserver(settingsObserver)
        } catch (_: Exception) {
        }
        accessibilityManager()?.removeAccessibilityStateChangeListener(a11yListener)
        if (instance === this) instance = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (maybeContinue("resume")) return
        if (!::openButton.isInitialized) return
        if (openedSettingsOnce) {
            retryHint.visibility = View.VISIBLE
            openButton.setText(R.string.onboarding_retry_button)
            Log.d(TAG, "返回时仍未启用，显示重试")
        }
    }

    private fun maybeContinue(reason: String): Boolean {
        if (continued || isFinishing) return continued
        if (!AccessibilitySettingsLauncher.isServiceEnabled(this)) return false
        Log.d(TAG, "服务已启用 ($reason)，进入主界面")
        continueToApp()
        return true
    }

    private fun continueToApp() {
        if (continued) return
        continued = true
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    private fun accessibilityManager(): AccessibilityManager? {
        return getSystemService(ACCESSIBILITY_SERVICE) as? AccessibilityManager
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
