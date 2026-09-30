package com.wechatblocker

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * 模拟视频号页面的测试Activity
 * 用于测试拦截功能
 */
class TestFinderActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val textView = TextView(this).apply {
            text = "测试视频号页面\n视频号\n关注\n推荐"
            textSize = 24f
            setPadding(50, 50, 50, 50)
        }
        
        setContentView(textView)
    }
}
