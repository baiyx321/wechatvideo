package com.wechatblocker.data

data class ReflectionEntry(
    val id: Long = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val passageSource: String = "",
    val passageText: String = ""
)
