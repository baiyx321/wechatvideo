package com.xingin.xhs

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            setBackgroundColor(Color.parseColor("#FE2C55"))
        }
        
        val title = TextView(this).apply {
            text = "小红书"
            textSize = 32f
            setTextColor(Color.WHITE)
            setPadding(20, 20, 20, 20)
        }
        
        val subtitle = TextView(this).apply {
            text = "标记我的生活"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(20, 20, 20, 20)
        }
        
        layout.addView(title)
        layout.addView(subtitle)
        
        setContentView(layout)
    }
}
