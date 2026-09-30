package com.wechatblocker.data

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class HistoryManager(private val context: Context) {
    private val historyFile = File(context.filesDir, "reflection_history.json")
    
    companion object {
        private const val TAG = "HistoryManager"
    }
    
    fun saveEntry(entry: ReflectionEntry) {
        try {
            val entries = getAllEntries().toMutableList()
            entries.add(0, entry.copy(id = System.currentTimeMillis()))
            saveEntries(entries)
            Log.d(TAG, "保存反思记录: ${entry.content.take(50)}...")
        } catch (e: Exception) {
            Log.e(TAG, "保存记录失败", e)
        }
    }
    
    fun getAllEntries(): List<ReflectionEntry> {
        try {
            if (!historyFile.exists()) {
                return emptyList()
            }
            
            val jsonString = historyFile.readText()
            val jsonArray = JSONArray(jsonString)
            val entries = mutableListOf<ReflectionEntry>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                entries.add(
                    ReflectionEntry(
                        id = obj.getLong("id"),
                        content = obj.getString("content"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
            
            return entries
        } catch (e: Exception) {
            Log.e(TAG, "读取历史记录失败", e)
            return emptyList()
        }
    }
    
    fun deleteEntry(id: Long) {
        try {
            val entries = getAllEntries().filter { it.id != id }
            saveEntries(entries)
            Log.d(TAG, "删除记录: $id")
        } catch (e: Exception) {
            Log.e(TAG, "删除记录失败", e)
        }
    }
    
    fun clearAll() {
        try {
            historyFile.delete()
            Log.d(TAG, "清空所有记录")
        } catch (e: Exception) {
            Log.e(TAG, "清空记录失败", e)
        }
    }
    
    private fun saveEntries(entries: List<ReflectionEntry>) {
        val jsonArray = JSONArray()
        entries.forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("content", entry.content)
            obj.put("timestamp", entry.timestamp)
            jsonArray.put(obj)
        }
        historyFile.writeText(jsonArray.toString(2))
    }
}
