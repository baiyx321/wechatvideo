package com.wechatblocker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyTypingMatcherTest {

    @Test
    fun cleanTextIgnoresPunctuationAndWhitespace() {
        val cleaned = CopyTypingMatcher.cleanText("学而时习之，不亦说乎？\n")
        assertEquals("学而时习之不亦说乎", cleaned)
    }

    @Test
    fun countsCorrectCharactersPositionByPosition() {
        val passage = "学而时习之，不亦说乎？"
        val input = "学而时习之不亦说乎"
        val result = CopyTypingMatcher.matchText(passage, input)
        assertEquals(9, result.correctCount)
        assertEquals(-1, result.firstMismatchIndex)
        assertTrue(result.mismatches.isEmpty())
    }

    @Test
    fun mismatchStopsCorrectCountForWrongChar() {
        val passage = "学而时习之"
        val input = "学而时习X"
        val result = CopyTypingMatcher.matchText(passage, input)
        assertEquals(4, result.correctCount)
        assertEquals(4, result.firstMismatchIndex)
        assertEquals(listOf(4), result.mismatches)
    }

    @Test
    fun extraInputCharactersAreMismatches() {
        val passage = "学而"
        val input = "学而时"
        val result = CopyTypingMatcher.matchText(passage, input)
        assertEquals(2, result.correctCount)
        assertEquals(2, result.firstMismatchIndex)
    }
}
