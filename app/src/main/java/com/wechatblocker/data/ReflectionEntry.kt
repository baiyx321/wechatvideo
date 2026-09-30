package com.wechatblocker.data

data class ReflectionEntry(
    val id: Long = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val passageSource: String = "",
    val passageText: String = "",
    val prompt: String = "",  // Alias for passageText
    val source: String = ""   // Alias for passageSource
) {
    // Compatibility getters
    fun getPrompt() = if (prompt.isNotEmpty()) prompt else passageText
    fun getSource() = if (source.isNotEmpty()) source else passageSource
}
