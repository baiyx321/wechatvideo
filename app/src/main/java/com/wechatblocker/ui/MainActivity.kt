package com.wechatblocker.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.wechatblocker.R
import com.wechatblocker.util.AccessibilitySettingsLauncher

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var enableButton: Button
    private var viewsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AccessibilitySettingsLauncher.isServiceEnabled(this)) {
            startOnboarding()
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        viewsReady = true

        statusText = findViewById(R.id.statusText)
        enableButton = findViewById(R.id.enableButton)

        enableButton.setOnClickListener {
            AccessibilitySettingsLauncher.open(this)
        }

        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.historyButton).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        findViewById<Button>(R.id.debugButton).setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing) return
        if (!AccessibilitySettingsLauncher.isServiceEnabled(this)) {
            startOnboarding()
            finish()
            return
        }
        if (viewsReady) {
            updateServiceStatus()
        }
    }

    private fun startOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    private fun updateServiceStatus() {
        val isEnabled = AccessibilitySettingsLauncher.isServiceEnabled(this)
        statusText.text = if (isEnabled) {
            getString(R.string.service_enabled)
        } else {
            getString(R.string.service_disabled)
        }

        statusText.setTextColor(
            if (isEnabled) {
                getColor(android.R.color.holo_green_dark)
            } else {
                getColor(android.R.color.holo_red_dark)
            }
        )
    }
}
