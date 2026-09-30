package com.ss.android.ugc.aweme

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
            setBackgroundColor(Color.BLACK)
        }
        
        val title = TextView(this).apply {
            text = "抖音"
            textSize = 32f
            setTextColor(Color.WHITE)
            setPadding(20, 20, 20, 20)
        }
        
        val subtitle = TextView(this).apply {
            text = "记录美好生活"
            textSize = 18f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(20, 20, 20, 20)
        }
        
        layout.addView(title)
        layout.addView(subtitle)
        
        setContentView(layout)
    }
}
