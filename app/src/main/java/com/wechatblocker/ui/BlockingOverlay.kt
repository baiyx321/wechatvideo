package com.wechatblocker.ui

import android.content.Context
import android.graphics.PixelFormat
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
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
import androidx.core.content.ContextCompat
import com.wechatblocker.R
import com.wechatblocker.data.CopyTypingMatcher
import com.wechatblocker.data.HistoryManager
import com.wechatblocker.data.Passage
import com.wechatblocker.data.PreferencesManager
import com.wechatblocker.data.ReflectionEntry
import com.wechatblocker.data.TextLibraryManager

class BlockingOverlay(
    private val context: Context,
    private val prefsManager: PreferencesManager,
    private val onDismiss: (success: Boolean) -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val historyManager = HistoryManager(context)
    private val textLibraryManager = TextLibraryManager(context)
    
    var overlayView: View? = null
        private set
    private var overlayContent: View? = null
    
    // 保存pending状态
    private var pendingPassage: Passage? = null
    private var pendingTypedText: String = ""
    private var availablePassages: List<Passage> = emptyList()
    
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
        val sourceText = view.findViewById<TextView>(R.id.sourceText)
        val promptText = view.findViewById<TextView>(R.id.promptText)
        val changePassageButton = view.findViewById<Button>(R.id.changePassageButton)
        val inputText = view.findViewById<EditText>(R.id.inputText)
        val mismatchHint = view.findViewById<TextView>(R.id.mismatchHint)
        val charCounter = view.findViewById<TextView>(R.id.charCounter)
        val submitButton = view.findViewById<Button>(R.id.submitButton)
        val backButton = view.findViewById<Button>(R.id.backButton)
        
        Log.d(TAG, "View查找结果: sourceText=$sourceText, promptText=$promptText, inputText=$inputText, charCounter=$charCounter, submitButton=$submitButton, backButton=$backButton")
        
        val isCopyTypingMode = prefsManager.copyTypingMode
        val minChars = prefsManager.minChars
        
        // 加载可用段落
        if (availablePassages.isEmpty() && isCopyTypingMode) {
            val enabledBooks = prefsManager.enabledBooks
            availablePassages = textLibraryManager.extractPassages(enabledBooks, minChars)
            Log.d(TAG, "加载了 ${availablePassages.size} 个段落")
        }
        
        if (isCopyTypingMode && availablePassages.isNotEmpty()) {
            // 抄写模式
            sourceText.visibility = View.VISIBLE
            changePassageButton.visibility = View.VISIBLE
            
            // 恢复或生成新的段落
            if (pendingPassage == null) {
                pendingPassage = availablePassages.random()
            }
            
            sourceText.text = "《${pendingPassage!!.source}》"
            promptText.text = pendingPassage!!.text
            Log.d(TAG, "设置段落: ${pendingPassage!!.source}, 长度=${pendingPassage!!.text.length}")
            
            // 恢复输入文本
            inputText.setText(pendingTypedText)
            inputText.setSelection(pendingTypedText.length)
            Log.d(TAG, "恢复输入文本: '$pendingTypedText'")
            
            // 初始匹配
            updateMatching(pendingPassage!!.text, pendingTypedText, charCounter, mismatchHint, submitButton, minChars)
            
            // 监听输入
            inputText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: Editable?) {
                    val input = s?.toString() ?: ""
                    pendingTypedText = input
                    updateMatching(pendingPassage!!.text, input, charCounter, mismatchHint, submitButton, minChars)
                }
            })
            
            // 换一段按钮
            changePassageButton.setOnClickListener {
                Log.d(TAG, "换一段被点击")
                pendingPassage = availablePassages.random()
                pendingTypedText = ""
                
                sourceText.text = "《${pendingPassage!!.source}》"
                promptText.text = pendingPassage!!.text
                inputText.setText("")
                mismatchHint.visibility = View.GONE
                updateMatching(pendingPassage!!.text, "", charCounter, mismatchHint, submitButton, minChars)
            }
            
        } else {
            // 自由书写模式
            sourceText.visibility = View.GONE
            changePassageButton.visibility = View.GONE
            mismatchHint.visibility = View.GONE
            
            // 使用旧的自由模式逻辑
            if (pendingPassage == null) {
                val prompt = prefsManager.getRandomPrompt()
                pendingPassage = Passage(prompt, "")
            }
            promptText.text = pendingPassage!!.text
            Log.d(TAG, "自由模式提示语: ${promptText.text}")
            
            inputText.setText(pendingTypedText)
            inputText.setSelection(pendingTypedText.length)
            
            val currentLength = pendingTypedText.length
            charCounter.text = "已输入 $currentLength / $minChars"
            submitButton.isEnabled = currentLength >= minChars
            
            inputText.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                
                override fun afterTextChanged(s: Editable?) {
                    val length = s?.length ?: 0
                    pendingTypedText = s?.toString() ?: ""
                    charCounter.text = "已输入 $length / $minChars"
                    submitButton.isEnabled = length >= minChars
                }
            })
        }
        
        // 提交按钮
        submitButton.setOnClickListener {
            val content = inputText.text.toString()
            Log.d(TAG, "提交按钮被点击,内容长度: ${content.length}, 最小要求: $minChars")
            
            val canSubmit = if (isCopyTypingMode && pendingPassage != null) {
                val matchResult = CopyTypingMatcher.matchText(pendingPassage!!.text, content)
                matchResult.correctCount >= minChars
            } else {
                content.length >= minChars
            }
            
            if (canSubmit) {
                Log.d(TAG, "用户提交反思")
                val source = if (isCopyTypingMode && pendingPassage != null) {
                    pendingPassage!!.source
                } else {
                    ""
                }
                historyManager.saveEntry(ReflectionEntry(
                    content = content, 
                    prompt = pendingPassage?.text ?: "",
                    source = source
                ))
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
    
    private fun updateMatching(
        passage: String,
        input: String,
        charCounter: TextView,
        mismatchHint: TextView,
        submitButton: Button,
        minChars: Int
    ) {
        val matchResult = CopyTypingMatcher.matchText(passage, input)
        
        charCounter.text = "正确 ${matchResult.correctCount} / $minChars"
        submitButton.isEnabled = matchResult.correctCount >= minChars
        
        if (matchResult.firstMismatchIndex >= 0) {
            val cleanPassage = CopyTypingMatcher.cleanText(passage)
            val cleanInput = CopyTypingMatcher.cleanText(input)
            val mismatchPos = matchResult.firstMismatchIndex
            
            if (mismatchPos < cleanInput.length) {
                val wrongChar = cleanInput[mismatchPos]
                val expectedChar = if (mismatchPos < cleanPassage.length) cleanPassage[mismatchPos] else '?'
                mismatchHint.text = "第 ${mismatchPos + 1} 个字错误: 输入了 '$wrongChar',应该是 '$expectedChar'"
                mismatchHint.visibility = View.VISIBLE
            } else {
                mismatchHint.visibility = View.GONE
            }
        } else {
            mismatchHint.visibility = View.GONE
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
        pendingPassage = null
        pendingTypedText = ""
        Log.d(TAG, "清除pending状态")
    }
    
    fun hasPendingState(): Boolean {
        return pendingPassage != null || pendingTypedText.isNotEmpty()
    }
    
    fun destroy() {
        hide()
        clearPendingState()
    }
}
