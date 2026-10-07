package com.wechatblocker.data

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class TextLibraryManager(private val context: Context) {

    companion object {
        private const val TAG = "TextLibraryManager"
        private const val TEXTS_DIR = "classics"
        const val CUSTOM_BOOK_ID = "custom"

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
            "guanzi" to "管子",
            CUSTOM_BOOK_ID to "自定义"
        )
    }

    fun loadBook(bookId: String): String {
        if (bookId == CUSTOM_BOOK_ID) return ""
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

    fun extractPassages(
        enabledBooks: Set<String>,
        minLength: Int = 50,
        maxLength: Int = 80,
        customText: String = "",
        maxPassages: Int = 80
    ): List<Passage> {
        val passages = mutableListOf<Passage>()

        for (bookId in enabledBooks) {
            if (passages.size >= maxPassages) break
            val bookName = BOOK_NAMES[bookId] ?: bookId
            val content = loadBook(bookId)
            if (content.isEmpty()) continue
            passages.addAll(
                PassageExtractor.extractPassages(content, bookName, minLength, maxLength, maxPassages - passages.size)
            )
        }

        if (customText.isNotBlank() && passages.size < maxPassages) {
            passages.addAll(
                PassageExtractor.extractPassages(
                    customText,
                    BOOK_NAMES[CUSTOM_BOOK_ID] ?: "自定义",
                    minLength,
                    maxLength,
                    maxPassages - passages.size
                )
            )
        }

        Log.d(TAG, "Extracted ${passages.size} passages from ${enabledBooks.size} books + custom=${customText.isNotBlank()}")
        return passages
    }
}
