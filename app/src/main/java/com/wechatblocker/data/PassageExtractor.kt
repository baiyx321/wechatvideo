package com.wechatblocker.data

object PassageExtractor {
    private val sentenceEnd = Regex("[。！？]")

    fun extractPassages(
        content: String,
        bookName: String,
        minLength: Int = 50,
        maxLength: Int = 80,
        maxPassages: Int = Int.MAX_VALUE
    ): List<Passage> {
        if (content.isBlank()) return emptyList()

        val passages = mutableListOf<Passage>()
        var currentChapter = ""
        val sentenceBuffer = mutableListOf<String>()

        fun flushIfReady(force: Boolean = false) {
            if (sentenceBuffer.isEmpty()) return
            val text = sentenceBuffer.joinToString("")
            val count = countHanzi(text)
            if (count >= minLength) {
                val source = if (currentChapter.isNotEmpty()) {
                    "$bookName·$currentChapter"
                } else {
                    bookName
                }
                passages.add(Passage(text.trim(), source))
                sentenceBuffer.clear()
            } else if (force) {
                sentenceBuffer.clear()
            }
        }

        val lines = content.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            if (passages.size >= maxPassages) break
            if (isChapterTitle(line)) {
                flushIfReady(force = true)
                currentChapter = normalizeChapter(line)
                continue
            }

            val sentences = splitSentences(line)
            for (sentence in sentences) {
                if (passages.size >= maxPassages) break
                val nextCount = countHanzi(sentenceBuffer.joinToString("") + sentence)
                if (sentenceBuffer.isNotEmpty() &&
                    countHanzi(sentenceBuffer.joinToString("")) >= minLength &&
                    nextCount > maxLength
                ) {
                    flushIfReady()
                }
                sentenceBuffer.add(sentence)
                val buffered = countHanzi(sentenceBuffer.joinToString(""))
                if (buffered in minLength..maxLength) {
                    flushIfReady()
                } else if (buffered > maxLength && sentenceBuffer.size == 1) {
                    // A single long sentence still counts as a whole-sentence passage.
                    flushIfReady()
                }
            }
        }
        flushIfReady(force = true)
        return passages
    }

    fun countHanzi(text: String): Int = text.count { it in '\u4e00'..'\u9fff' }

    private fun splitSentences(line: String): List<String> {
        val parts = mutableListOf<String>()
        val builder = StringBuilder()
        for (ch in line) {
            builder.append(ch)
            if (sentenceEnd.matches(ch.toString())) {
                val sentence = builder.toString().trim()
                if (sentence.isNotEmpty()) parts.add(sentence)
                builder.clear()
            }
        }
        val tail = builder.toString().trim()
        if (tail.isNotEmpty()) parts.add(tail)
        return parts
    }

    private fun isChapterTitle(line: String): Boolean {
        val cleaned = line.removePrefix("##").trim()
        return cleaned.length in 1..24 &&
            !cleaned.contains('。') &&
            !cleaned.contains('，') &&
            !cleaned.contains('！') &&
            !cleaned.contains('？')
    }

    private fun normalizeChapter(line: String): String {
        var name = line.removePrefix("##").trim()
        val idx = name.indexOf('篇')
        if (idx >= 0 && idx < name.length - 1) {
            name = name.substring(idx + 1)
        }
        return name.trim('《', '》', ' ')
    }
}
