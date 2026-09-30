package com.wechatblocker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wechatblocker.R
import com.wechatblocker.service.WeChatBlockerService

class DebugActivity : AppCompatActivity() {
    
    private lateinit var packageText: TextView
    private lateinit var classText: TextView
    private lateinit var textsText: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var isAutoRefreshing = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.debug_calibration)
        
        packageText = findViewById(R.id.packageText)
        classText = findViewById(R.id.classText)
        textsText = findViewById(R.id.textsText)
        
        val refreshButton = findViewById<Button>(R.id.refreshButton)
        val copyPackageButton = findViewById<Button>(R.id.copyPackageButton)
        val copyClassButton = findViewById<Button>(R.id.copyClassButton)
        val copyTextsButton = findViewById<Button>(R.id.copyTextsButton)
        
        refreshButton.setOnClickListener {
            refreshDebugInfo()
        }
        
        copyPackageButton.setOnClickListener {
            copyToClipboard(WeChatBlockerService.latestPackageName ?: "")
        }
        
        copyClassButton.setOnClickListener {
            copyToClipboard(WeChatBlockerService.latestClassName ?: "")
        }
        
        copyTextsButton.setOnClickListener {
            copyToClipboard(WeChatBlockerService.latestVisibleTexts.joinToString("\n"))
        }
        
        refreshDebugInfo()
        startAutoRefresh()
    }
    
    private fun refreshDebugInfo() {
        val service = WeChatBlockerService.instance
        
        if (service == null) {
            packageText.text = getString(R.string.toast_service_not_enabled)
            classText.text = getString(R.string.debug_no_data)
            textsText.text = getString(R.string.debug_no_data)
            return
        }
        
        packageText.text = WeChatBlockerService.latestPackageName ?: getString(R.string.debug_no_data)
        classText.text = WeChatBlockerService.latestClassName ?: getString(R.string.debug_no_data)
        
        val texts = WeChatBlockerService.latestVisibleTexts
        if (texts.isEmpty()) {
            textsText.text = getString(R.string.debug_no_data)
        } else {
            textsText.text = texts.take(20).joinToString("\n") { "• $it" }
        }
    }
    
    private fun startAutoRefresh() {
        isAutoRefreshing = true
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (isAutoRefreshing) {
                    refreshDebugInfo()
                    handler.postDelayed(this, 2000)
                }
            }
        }, 2000)
    }
    
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("debug_info", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isAutoRefreshing = false
        handler.removeCallbacksAndMessages(null)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
