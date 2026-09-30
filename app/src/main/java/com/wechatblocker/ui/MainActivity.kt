package com.wechatblocker.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.wechatblocker.R
import com.wechatblocker.service.WeChatBlockerService

class MainActivity : AppCompatActivity() {
    
    private lateinit var statusText: TextView
    private lateinit var enableButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        statusText = findViewById(R.id.statusText)
        enableButton = findViewById(R.id.enableButton)
        
        enableButton.setOnClickListener {
            openAccessibilitySettings()
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
        updateServiceStatus()
    }
    
    private fun updateServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
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
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "$packageName/${WeChatBlockerService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(serviceName) == true
    }
    
    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
