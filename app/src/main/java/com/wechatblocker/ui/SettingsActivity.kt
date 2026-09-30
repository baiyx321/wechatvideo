package com.wechatblocker.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.wechatblocker.R
import com.wechatblocker.data.PreferencesManager

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var prefsManager: PreferencesManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        prefsManager = PreferencesManager(this)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)
        
        val minCharsEdit = findViewById<EditText>(R.id.minCharsEdit)
        val cooldownEdit = findViewById<EditText>(R.id.cooldownEdit)
        val enabledSwitch = findViewById<Switch>(R.id.enabledSwitch)
        val promptsEdit = findViewById<EditText>(R.id.promptsEdit)
        val classKeywordsEdit = findViewById<EditText>(R.id.classKeywordsEdit)
        val textKeywordsEdit = findViewById<EditText>(R.id.textKeywordsEdit)
        val targetPackagesEdit = findViewById<EditText>(R.id.targetPackagesEdit)
        val saveButton = findViewById<Button>(R.id.saveButton)
        
        // 加载当前设置
        minCharsEdit.setText(prefsManager.minChars.toString())
        cooldownEdit.setText(prefsManager.cooldownMinutes.toString())
        enabledSwitch.isChecked = prefsManager.enabled
        promptsEdit.setText(prefsManager.prompts)
        classKeywordsEdit.setText(prefsManager.classKeywords)
        textKeywordsEdit.setText(prefsManager.textKeywords)
        targetPackagesEdit.setText(prefsManager.targetPackages)
        
        // 保存按钮
        saveButton.setOnClickListener {
            try {
                prefsManager.minChars = minCharsEdit.text.toString().toIntOrNull() ?: 50
                prefsManager.cooldownMinutes = cooldownEdit.text.toString().toIntOrNull() ?: 10
                prefsManager.enabled = enabledSwitch.isChecked
                prefsManager.prompts = promptsEdit.text.toString()
                prefsManager.classKeywords = classKeywordsEdit.text.toString()
                prefsManager.textKeywords = textKeywordsEdit.text.toString()
                prefsManager.targetPackages = targetPackagesEdit.text.toString()
                
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
