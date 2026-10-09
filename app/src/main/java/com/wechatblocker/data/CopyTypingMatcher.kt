package com.wechatblocker.data

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan

data class Passage(
    val text: String,
    val source: String
)

data class MatchResult(
    val correctCount: Int,
    val totalRequired: Int,
    val mismatches: List<Int>,
    val firstMismatchIndex: Int
)

object CopyTypingMatcher {

    fun matchText(passage: String, input: String): MatchResult {
        val cleanPassage = cleanText(passage)
        val cleanInput = cleanText(input)

        var correctCount = 0
        val mismatches = mutableListOf<Int>()

        for (i in cleanInput.indices) {
            if (i >= cleanPassage.length) {
                mismatches.add(i)
                continue
            }
            if (cleanInput[i] == cleanPassage[i]) {
                correctCount++
            } else {
                mismatches.add(i)
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
        return buildString {
            for (ch in text) {
                if (ch.isWhitespace() || isPunctuation(ch)) continue
                append(ch)
            }
        }
    }

    fun highlightAgainstPassage(passage: String, input: String, mismatchColor: Int): Spannable {
        val cleaned = cleanText(input)
        val cleanPassage = cleanText(passage)
        val spannable = SpannableString(cleaned)
        for (i in cleaned.indices) {
            val expected = if (i < cleanPassage.length) cleanPassage[i] else null
            if (expected == null || cleaned[i] != expected) {
                spannable.setSpan(
                    ForegroundColorSpan(mismatchColor),
                    i,
                    i + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return spannable
    }

    fun highlightOriginalInput(passage: String, input: String, mismatchColor: Int): Spannable {
        val cleanPassage = cleanText(passage)
        val spannable = SpannableString(input)
        var cleanIndex = 0
        for (i in input.indices) {
            val ch = input[i]
            if (ch.isWhitespace() || isPunctuation(ch)) continue
            val expected = if (cleanIndex < cleanPassage.length) cleanPassage[cleanIndex] else null
            if (expected == null || ch != expected) {
                spannable.setSpan(
                    ForegroundColorSpan(mismatchColor),
                    i,
                    i + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            cleanIndex++
        }
        return spannable
    }

    private fun isPunctuation(ch: Char): Boolean {
        return when (Character.getType(ch)) {
            Character.CONNECTOR_PUNCTUATION.toInt(),
            Character.DASH_PUNCTUATION.toInt(),
            Character.END_PUNCTUATION.toInt(),
            Character.FINAL_QUOTE_PUNCTUATION.toInt(),
            Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
            Character.OTHER_PUNCTUATION.toInt(),
            Character.START_PUNCTUATION.toInt() -> true
            else -> ch in "·—…《》〈〉「」『』【】（）()[]{}\"'“”‘’"
        }
    }
}
