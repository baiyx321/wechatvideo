package com.wechatblocker.data

data class Passage(
    val text: String,
    val source: String
)

object CopyTypingMatcher {
    
    fun matchText(passage: String, input: String): MatchResult {
        val cleanPassage = cleanText(passage)
        val cleanInput = cleanText(input)
        
        var correctCount = 0
        val mismatches = mutableListOf<Int>()
        
        for (i in cleanInput.indices) {
            if (i < cleanPassage.length) {
                if (cleanInput[i] == cleanPassage[i]) {
                    correctCount++
                } else {
                    mismatches.add(i)
                }
            }
        }
        
        return MatchResult(
            correctCount = correctCount,
            totalRequired = cleanPassage.length,
            mismatches = mismatches,
            firstMismatchIndex = mismatches.firstOrNull() ?: -1
        )
    }
    
    fun cleanText(text: String): String {
        var result = text
        result = result.replace(Regex("\\s+"), "")
        val punctuation = listOf("，", "。", "！", "？", "、", "；", "：", """, """, "'", "'", "（", "）", "《", "》", "【", "】")
        for (p in punctuation) {
            result = result.replace(p, "")
        }
        return result
    }
}

data class MatchResult(
    val correctCount: Int,
    val totalRequired: Int,
    val mismatches: List<Int>,
    val firstMismatchIndex: Int
)
