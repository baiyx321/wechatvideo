package com.wechatblocker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassageExtractorTest {

    @Test
    fun extractsConsecutiveSentencesInRange() {
        val sentence = "君子曰学不可以已青取之于蓝而青于蓝。"
        val content = "## 劝学\n\n" + sentence.repeat(4)
        val passages = PassageExtractor.extractPassages(content, "荀子", minLength = 50, maxLength = 80)
        assertTrue(passages.isNotEmpty())
        passages.forEach { passage ->
            val count = PassageExtractor.countHanzi(passage.text)
            assertTrue("unexpected length $count for ${passage.text}", count >= 50)
            assertTrue(passage.source.contains("荀子"))
        }
    }

    @Test
    fun usesChapterAsSource() {
        val body = "仓廪实则知礼节。衣食足则知荣辱。上服度则六亲固。四维不张国乃灭亡。下令如流水之原。令顺民心故论卑而易行。俗之所欲因而予之。"
        val passages = PassageExtractor.extractPassages("## 牧民\n\n$body", "管子", 50, 80)
        assertTrue(passages.isNotEmpty())
        assertEquals("管子·牧民", passages.first().source)
    }
}
