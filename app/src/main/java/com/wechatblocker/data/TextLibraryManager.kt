package com.wechatblocker.data

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class TextLibraryManager(private val context: Context) {
    
    companion object {
        private const val TAG = "TextLibraryManager"
        private const val TEXTS_DIR = "texts"
        
        val DEFAULT_BOOKS = listOf(
            "lunyu",
            "daxue", 
            "zhongyong",
            "mengzi",
            "xunzi",
            "guanzi"
        )
        
        val BOOK_NAMES = mapOf(
            "lunyu" to "论语",
            "daxue" to "大学",
            "zhongyong" to "中庸",
            "mengzi" to "孟子",
            "xunzi" to "荀子",
            "guanzi" to "管子"
        )
    }
    
    fun loadBook(bookId: String): String {
        return try {
            val inputStream = context.assets.open("$TEXTS_DIR/$bookId.txt")
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val content = reader.readText()
            reader.close()
            Log.d(TAG, "Loaded $bookId: ${content.length} characters")
            content
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load $bookId", e)
            ""
        }
    }
    
    fun extractPassages(enabledBooks: Set<String>, minLength: Int = 50): List<Passage> {
        val passages = mutableListOf<Passage>()
        
        for (bookId in enabledBooks) {
            val bookName = BOOK_NAMES[bookId] ?: bookId
            val content = loadBook(bookId)
            if (content.isEmpty()) continue
            
            val lines = content.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            var currentChapter = ""
            var buffer = StringBuilder()
            
            for (line in lines) {
                if (isChapterTitle(line)) {
                    if (buffer.isNotEmpty()) {
                        val text = buffer.toString().trim()
                        if (countHanzi(text) >= minLength) {
                            val source = if (currentChapter.isNotEmpty()) {
                                "$bookName·$currentChapter"
                            } else {
                                bookName
                            }
                            passages.add(Passage(text, source))
                        }
                        buffer.clear()
                    }
                    currentChapter = line
                } else {
                    buffer.append(line)
                    
                    val hanziCount = countHanzi(buffer.toString())
                    if (hanziCount >= minLength && hanziCount <= 150 && 
                        (line.endsWith("。") || line.endsWith("！") || line.endsWith("？") || 
                         line.endsWith("也") || line.endsWith("矣") || line.endsWith("乎"))) {
                        val text = buffer.toString().trim()
                        val source = if (currentChapter.isNotEmpty()) {
                            "$bookName·$currentChapter"
                        } else {
                            bookName
                        }
                        passages.add(Passage(text, source))
                        buffer.clear()
                    }
                }
            }
            
            if (buffer.isNotEmpty()) {
                val text = buffer.toString().trim()
                if (countHanzi(text) >= minLength) {
                    val source = if (currentChapter.isNotEmpty()) {
                        "$bookName·$currentChapter"
                    } else {
                        bookName
                    }
                    passages.add(Passage(text, source))
                }
            }
        }
        
        Log.d(TAG, "Extracted ${passages.size} passages from ${enabledBooks.size} books")
        return passages
    }
    
    private fun isChapterTitle(line: String): Boolean {
        return line.length < 20 && !line.contains("。") && !line.contains("，")
    }
    
    private fun countHanzi(text: String): Int {
        return text.count { it in '\u4e00'..'\u9fff' }
    }
}
