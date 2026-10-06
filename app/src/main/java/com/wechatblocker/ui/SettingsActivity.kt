package com.wechatblocker.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.wechatblocker.R
import com.wechatblocker.data.PreferencesManager
import com.wechatblocker.service.WeChatBlockerService

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefsManager: PreferencesManager
    private lateinit var customTextStatus: TextView

    private val importTextLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) {
            Log.d(TAG, "用户取消导入")
            return@registerForActivityResult
        }
        importCustomText(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefsManager = PreferencesManager(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings)

        val enableWechatSwitch = findViewById<Switch>(R.id.enableWechatSwitch)
        val enableDouyinSwitch = findViewById<Switch>(R.id.enableDouyinSwitch)
        val enableXiaohongshuSwitch = findViewById<Switch>(R.id.enableXiaohongshuSwitch)
        val awayMinutesEdit = findViewById<EditText>(R.id.awayMinutesEdit)
        val nightHourEdit = findViewById<EditText>(R.id.nightHourEdit)
        val nightMinuteEdit = findViewById<EditText>(R.id.nightMinuteEdit)
        val copyTypingModeSwitch = findViewById<Switch>(R.id.copyTypingModeSwitch)
        val minCharsEdit = findViewById<EditText>(R.id.minCharsEdit)
        val bookLunyu = findViewById<CheckBox>(R.id.bookLunyu)
        val bookDaxue = findViewById<CheckBox>(R.id.bookDaxue)
        val bookZhongyong = findViewById<CheckBox>(R.id.bookZhongyong)
        val bookMengzi = findViewById<CheckBox>(R.id.bookMengzi)
        val bookXunzi = findViewById<CheckBox>(R.id.bookXunzi)
        val bookGuanzi = findViewById<CheckBox>(R.id.bookGuanzi)
        customTextStatus = findViewById(R.id.customTextStatus)

        enableWechatSwitch.isChecked = prefsManager.enableWechat
        enableDouyinSwitch.isChecked = prefsManager.enableDouyin
        enableXiaohongshuSwitch.isChecked = prefsManager.enableXiaohongshu
        awayMinutesEdit.setText(prefsManager.awayMinutes.toString())
        nightHourEdit.setText(prefsManager.nightHour.toString())
        nightMinuteEdit.setText(prefsManager.nightMinute.toString())
        copyTypingModeSwitch.isChecked = prefsManager.copyTypingMode
        minCharsEdit.setText(prefsManager.minChars.toString())

        val enabledBooks = prefsManager.enabledBooks
        bookLunyu.isChecked = enabledBooks.contains("lunyu")
        bookDaxue.isChecked = enabledBooks.contains("daxue")
        bookZhongyong.isChecked = enabledBooks.contains("zhongyong")
        bookMengzi.isChecked = enabledBooks.contains("mengzi")
        bookXunzi.isChecked = enabledBooks.contains("xunzi")
        bookGuanzi.isChecked = enabledBooks.contains("guanzi")
        refreshCustomStatus()

        findViewById<Button>(R.id.importTextButton).setOnClickListener {
            importTextLauncher.launch(arrayOf("text/plain", "text/*"))
        }

        findViewById<Button>(R.id.shortenAwayButton).setOnClickListener {
            prefsManager.awayMinutes = 0
            awayMinutesEdit.setText("0")
            WeChatBlockerService.instance?.shortenAwayForDebug()
            Log.d(TAG, "缩短离开时间为 0 分钟")
            Toast.makeText(this, "离开时间已设为 0 分钟", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.triggerNightButton).setOnClickListener {
            val shown = WeChatBlockerService.instance?.triggerNightCheckNow() ?: false
            Log.d(TAG, "立即触发夜间检查 shown=$shown service=${WeChatBlockerService.instance != null}")
            val msg = if (WeChatBlockerService.instance == null) {
                "服务未启用，无法触发"
            } else if (shown) {
                "已立即显示夜间覆盖层"
            } else {
                "已标记夜间检查，打开目标应用后触发"
            }
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            try {
                prefsManager.enableWechat = enableWechatSwitch.isChecked
                prefsManager.enableDouyin = enableDouyinSwitch.isChecked
                prefsManager.enableXiaohongshu = enableXiaohongshuSwitch.isChecked
                prefsManager.awayMinutes = awayMinutesEdit.text.toString().toIntOrNull()?.coerceAtLeast(0) ?: 5
                prefsManager.nightHour = nightHourEdit.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: 23
                prefsManager.nightMinute = nightMinuteEdit.text.toString().toIntOrNull()?.coerceIn(0, 59) ?: 0
                prefsManager.copyTypingMode = copyTypingModeSwitch.isChecked
                prefsManager.minChars = minCharsEdit.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 50

                val selected = mutableSetOf<String>()
                if (bookLunyu.isChecked) selected.add("lunyu")
                if (bookDaxue.isChecked) selected.add("daxue")
                if (bookZhongyong.isChecked) selected.add("zhongyong")
                if (bookMengzi.isChecked) selected.add("mengzi")
                if (bookXunzi.isChecked) selected.add("xunzi")
                if (bookGuanzi.isChecked) selected.add("guanzi")
                if (selected.isEmpty()) {
                    Toast.makeText(this, "请至少选择一本书", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefsManager.enabledBooks = selected
                Log.d(TAG, "设置已保存 books=$selected away=${prefsManager.awayMinutes} night=${prefsManager.nightTimeLabel()}")
                Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "保存失败", e)
                Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun importCustomText(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (text.isBlank()) {
                Toast.makeText(this, "文件为空", Toast.LENGTH_SHORT).show()
                return
            }
            prefsManager.customTexts = text
            Log.d(TAG, "导入自定义文本 ${text.length} 字符")
            refreshCustomStatus()
            Toast.makeText(this, "已导入自定义文本", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "导入失败", e)
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshCustomStatus() {
        val text = prefsManager.customTexts
        customTextStatus.text = if (text.isBlank()) {
            "未导入自定义文本"
        } else {
            "已导入自定义文本，约 ${text.length} 字符"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val TAG = "SettingsActivity"
    }
}
