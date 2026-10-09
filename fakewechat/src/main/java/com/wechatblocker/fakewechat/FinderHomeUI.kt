package com.wechatblocker.fakewechat

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 假微信视频号Activity
 * 类名包含Finder关键词,模拟真实视频号
 */
class FinderHomeUI : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }
        
        // 添加视频号特征文本
        val texts = listOf(
            "视频号" to 28f,
            "关注" to 20f,
            "朋友" to 20f,
            "推荐" to 20f
        )
        
        texts.forEach { (text, size) ->
            layout.addView(TextView(this).apply {
                this.text = text
                textSize = size
                setPadding(20, 20, 20, 20)
            })
        }
        
        setContentView(layout)
    }
}
