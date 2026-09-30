package com.wechatblocker.logic

import org.junit.Assert.*
import org.junit.Test

class BlockingLogicTest {
    
    @Test
    fun `test shouldBlock returns false when disabled`() {
        val logic = BlockingLogic(
            minChars = 50,
            cooldownMinutes = 10,
            classKeywords = listOf("finder"),
            textKeywords = listOf("视频号"),
            targetPackages = listOf("com.tencent.mm"),
            enabled = false
        )
        
        val result = logic.shouldBlock(
            packageName = "com.tencent.mm",
            className = "FinderHomeUI",
            visibleTexts = listOf("视频号", "关注"),
            lastPassTime = 0
        )
        
        assertFalse("当功能禁用时应返回false", result)
    }
    
    @Test
    fun `test shouldBlock returns false for non-target package`() {
        val logic = BlockingLogic(
            minChars = 50,
            cooldownMinutes = 10,
            classKeywords = listOf("finder"),
            textKeywords = listOf("视频号"),
            targetPackages = listOf("com.tencent.mm"),
            enabled = true
        )
        
        val result = logic.shouldBlock(
            packageName = "com.other.app",
            className = "FinderHomeUI",
            visibleTexts = listOf("视频号", "关注"),
            lastPassTime = 0
        )
        
        assertFalse("非目标包名应返回false", result)
    }
    
    @Test
    fun `test shouldBlock returns false during cooldown`() {
        val logic = BlockingLogic(
            minChars = 50,
            cooldownMinutes = 10,
            classKeywords = listOf("finder"),
            textKeywords = listOf("视频号"),
            targetPackages = listOf("com.tencent.mm"),
            enabled = true
        )
        
        val currentTime = System.currentTimeMillis()
        val lastPassTime = currentTime - (5 * 60 * 1000) // 5分钟前
        
        val result = logic.shouldBlock(
            packageName = "com.tencent.mm",
            className = "FinderHomeUI",
            visibleTexts = listOf("视频号", "关注"),
            lastPassTime = lastPassTime,
            currentTime = currentTime
        )
        
        assertFalse("冷却期内应返回false", result)
    }
    
    @Test
    fun `test shouldBlock returns true after cooldown expires`() {
        val logic = BlockingLogic(
            minChars = 50,
            cooldownMinutes = 10,
            classKeywords = listOf("finder"),
            textKeywords = listOf("视频号"),
            targetPackages = listOf("com.tencent.mm"),
            enabled = true
        )
        
        val currentTime = System.currentTimeMillis()
        val lastPassTime = currentTime - (11 * 60 * 1000) // 11分钟前
        
        val result = logic.shouldBlock(
            packageName = "com.tencent.mm",
            className = "FinderHomeUI",
            visibleTexts = listOf("视频号", "关注"),
            lastPassTime = lastPassTime,
            currentTime = currentTime
        )
        
        assertTrue("冷却期过后且检测到视频号应返回true", result)
    }
    
    @Test
    fun `test isFinderPage detects by class name`() {
        val logic = BlockingLogic(
            minChars = 50,
            cooldownMinutes = 10,
            classKeywords = listOf("finder", "FinderUI"),
            textKeywords = listOf("视频号"),
            targetPackages = listOf("com.tencent.mm"),
            enabled = true
        )
        
        assertTrue(logic.isFinderPage("FinderHomeUI", emptyList()))
        assertTrue(logic.isFinderPage("com.tencent.mm.plugin.finder.ui.FinderUI", emptyList()))
        assertFalse(logic.isFinderPage("LauncherUI", emptyList()))
    }
    
    @Test
    fun `test isFinderPage detects by visible texts`() {
        val logic = BlockingLogic(
            minChars = 50,
            cooldownMinutes = 10,
            classKeywords = listOf("finder"),
            textKeywords = listOf("视频号", "关注", "朋友", "推荐"),
            targetPackages = listOf("com.tencent.mm"),
            enabled = true
        )
        
        // 需要至少2个关键词匹配
        assertTrue(logic.isFinderPage(null, listOf("视频号", "关注", "其他文本")))
        assertTrue(logic.isFinderPage(null, listOf("朋友", "推荐")))
        assertFalse(logic.isFinderPage(null, listOf("视频号", "其他文本")))
        assertFalse(logic.isFinderPage(null, listOf("完全不相关")))
    }
    
    @Test
    fun `test isValidSubmission checks minimum length`() {
        val logic = BlockingLogic(
            minChars = 50,
            cooldownMinutes = 10,
            classKeywords = listOf("finder"),
            textKeywords = listOf("视频号"),
            targetPackages = listOf("com.tencent.mm"),
            enabled = true
        )
        
        assertFalse(logic.isValidSubmission("短文本"))
        assertFalse(logic.isValidSubmission("a".repeat(49)))
        assertTrue(logic.isValidSubmission("a".repeat(50)))
        assertTrue(logic.isValidSubmission("a".repeat(100)))
    }
    
    @Test
    fun `test getRemainingChars calculates correctly`() {
        val logic = BlockingLogic(
            minChars = 50,
            cooldownMinutes = 10,
            classKeywords = listOf("finder"),
            textKeywords = listOf("视频号"),
            targetPackages = listOf("com.tencent.mm"),
            enabled = true
        )
        
        assertEquals(50, logic.getRemainingChars(""))
        assertEquals(30, logic.getRemainingChars("a".repeat(20)))
        assertEquals(0, logic.getRemainingChars("a".repeat(50)))
        assertEquals(0, logic.getRemainingChars("a".repeat(100)))
    }
    
    @Test
    fun `test case insensitive class name matching`() {
        val logic = BlockingLogic(
            minChars = 50,
            cooldownMinutes = 10,
            classKeywords = listOf("finder"),
            textKeywords = listOf("视频号"),
            targetPackages = listOf("com.tencent.mm"),
            enabled = true
        )
        
        assertTrue(logic.isFinderPage("FinderUI", emptyList()))
        assertTrue(logic.isFinderPage("finderUI", emptyList()))
        assertTrue(logic.isFinderPage("FINDERUI", emptyList()))
    }
}
