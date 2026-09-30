package com.wechatblocker.ui

import android.content.Context
import android.graphics.PixelFormat
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.wechatblocker.R
import com.wechatblocker.data.HistoryManager
import com.wechatblocker.data.PreferencesManager
import com.wechatblocker.data.ReflectionEntry

class BlockingOverlay(
    private val context: Context,
    private val prefsManager: PreferencesManager,
    private val onDismiss: (success: Boolean) -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val historyManager = HistoryManager(context)
    var overlayView: View? = null
        private set
    private var overlayContent: View? = null
    
    // 保存pending状态
    private var pendingPrompt: String? = null
    private var pendingTypedText: String = ""
    
    companion object {
        private const val TAG = "BlockingOverlay"
    }
    
    fun show() {
        if (overlayView != null) {
            Log.d(TAG, "覆盖层已存在")
            return
        }
        
        try {
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                format = PixelFormat.TRANSLUCENT
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            
            val container = FrameLayout(context).apply {
                isClickable = true
                isFocusable = false
                setOnTouchListener { v, event ->
                    Log.d(TAG, "容器收到触摸事件: action=${event.action}, x=${event.x}, y=${event.y}")
                    false
                }
            }
            
            overlayContent = LayoutInflater.from(context).inflate(R.layout.overlay_blocking, container, false)
            container.addView(overlayContent)
            Log.d(TAG, "布局inflate完成: ${overlayContent != null}")
            
            setupOverlayView(overlayContent!!)
            
            windowManager.addView(container, layoutParams)
            overlayView = container
            Log.d(TAG, "覆盖层已显示, view=$container, params=$layoutParams")
            
        } catch (e: Exception) {
            Log.e(TAG, "显示覆盖层失败", e)
        }
    }
    
    private fun setupOverlayView(view: View) {
        Log.d(TAG, "开始设置覆盖层视图")
        val promptText = view.findViewById<TextView>(R.id.promptText)
        val inputText = view.findViewById<EditText>(R.id.inputText)
        val charCounter = view.findViewById<TextView>(R.id.charCounter)
        val submitButton = view.findViewById<Button>(R.id.submitButton)
        val backButton = view.findViewById<Button>(R.id.backButton)
        
        Log.d(TAG, "View查找结果: promptText=$promptText, inputText=$inputText, charCounter=$charCounter, submitButton=$submitButton, backButton=$backButton")
        
        // 恢复或生成新的提示语
        if (pendingPrompt == null) {
            pendingPrompt = prefsManager.getRandomPrompt()
        }
        promptText.text = pendingPrompt
        Log.d(TAG, "设置提示语: ${promptText.text}")
        
        // 恢复输入文本
        inputText.setText(pendingTypedText)
        inputText.setSelection(pendingTypedText.length)
        Log.d(TAG, "恢复输入文本: '$pendingTypedText'")
        
        // 设置字符计数器
        val minChars = prefsManager.minChars
        val currentLength = pendingTypedText.length
        charCounter.text = context.getString(R.string.block_char_count, currentLength, minChars)
        submitButton.isEnabled = currentLength >= minChars
        
        // 监听输入
        inputText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: Editable?) {
                val length = s?.length ?: 0
                pendingTypedText = s?.toString() ?: ""
                charCounter.text = context.getString(R.string.block_char_count, length, minChars)
                submitButton.isEnabled = length >= minChars
            }
        })
        
        // 提交按钮
        submitButton.setOnClickListener {
            val content = inputText.text.toString()
            Log.d(TAG, "提交按钮被点击,内容长度: ${content.length}, 最小要求: $minChars")
            if (content.length >= minChars) {
                Log.d(TAG, "用户提交反思,长度: ${content.length}")
                historyManager.saveEntry(ReflectionEntry(content = content))
                clearPendingState()
                hide()
                onDismiss(true)
            } else {
                Log.d(TAG, "内容不足,无法提交")
            }
        }
        
        // 返回按钮
        backButton.setOnClickListener {
            Log.d(TAG, "返回按钮被点击")
            clearPendingState()
            hide()
            onDismiss(false)
        }
        
        // 添加OnLayoutChangeListener来记录按钮位置
        submitButton.addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
            val location = IntArray(2)
            v.getLocationOnScreen(location)
            val centerX = location[0] + (right - left) / 2
            val centerY = location[1] + (bottom - top) / 2
            Log.d(TAG, "提交按钮位置: screen=(${location[0]},${location[1]}), " +
                    "size=${right-left}x${bottom-top}, center=($centerX,$centerY)")
            Log.d(TAG, "提交按钮状态: enabled=${v.isEnabled}, clickable=${v.isClickable}")
        }
        
        backButton.addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
            val location = IntArray(2)
            v.getLocationOnScreen(location)
            val centerX = location[0] + (right - left) / 2
            val centerY = location[1] + (bottom - top) / 2
            Log.d(TAG, "返回按钮位置: screen=(${location[0]},${location[1]}), " +
                    "size=${right-left}x${bottom-top}, center=($centerX,$centerY)")
        }
        
        // 给按钮添加touch listener来调试触摸事件
        submitButton.setOnTouchListener { v, event ->
            Log.d(TAG, "提交按钮收到触摸: action=${event.actionMasked}, x=${event.x}, y=${event.y}, " +
                    "rawX=${event.rawX}, rawY=${event.rawY}")
            false  // 不拦截,让onClick处理
        }
        
        backButton.setOnTouchListener { v, event ->
            Log.d(TAG, "返回按钮收到触摸: action=${event.actionMasked}, x=${event.x}, y=${event.y}, " +
                    "rawX=${event.rawX}, rawY=${event.rawY}")
            false
        }
    }
    
    fun hide() {
        try {
            overlayView?.let {
                windowManager.removeView(it)
                overlayView = null
                Log.d(TAG, "覆盖层已隐藏 (pending状态保留)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "隐藏覆盖层失败", e)
        }
    }
    
    fun clearPendingState() {
        pendingPrompt = null
        pendingTypedText = ""
        Log.d(TAG, "清除pending状态")
    }
    
    fun hasPendingState(): Boolean {
        return pendingPrompt != null || pendingTypedText.isNotEmpty()
    }
    
    fun destroy() {
        hide()
        clearPendingState()
    }
}
