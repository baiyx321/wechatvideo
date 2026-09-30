package com.wechatblocker

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 测试目标Activity - 模拟视频号页面
 * 类名包含"Finder"关键词,文本包含"视频号"等关键词
 */
class TestFinderActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 修改配置以包含自己的包名(用于测试)
        val prefs = getSharedPreferences("wechat_blocker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("target_packages", "com.tencent.mm,com.wechatblocker").apply()
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        
        // 添加模拟视频号界面的文本
        layout.addView(TextView(this).apply {
            text = "视频号"
            textSize = 24f
            contentDescription = "视频号标题"
        })
        
        layout.addView(TextView(this).apply {
            text = "关注"
            textSize = 18f
        })
        
        layout.addView(TextView(this).apply {
            text = "朋友"
            textSize = 18f
        })
        
        layout.addView(TextView(this).apply {
            text = "推荐"
            textSize = 18f
        })
        
        setContentView(layout)
    }
}
