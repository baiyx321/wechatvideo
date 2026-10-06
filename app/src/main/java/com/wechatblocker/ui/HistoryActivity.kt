package com.wechatblocker.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wechatblocker.R
import com.wechatblocker.data.HistoryManager
import com.wechatblocker.data.ReflectionEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {
    
    private lateinit var historyManager: HistoryManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: HistoryAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.history)
        
        historyManager = HistoryManager(this)
        
        recyclerView = findViewById(R.id.recyclerView)
        emptyText = findViewById(R.id.emptyText)
        val clearButton = findViewById<Button>(R.id.clearButton)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = HistoryAdapter(
            onDelete = { entry ->
                historyManager.deleteEntry(entry.id)
                loadHistory()
                Toast.makeText(this, R.string.toast_deleted, Toast.LENGTH_SHORT).show()
            }
        )
        recyclerView.adapter = adapter
        
        clearButton.setOnClickListener {
            showClearAllDialog()
        }
        
        loadHistory()
    }
    
    private fun loadHistory() {
        val entries = historyManager.getAllEntries()
        adapter.setData(entries)
        
        if (entries.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyText.visibility = View.GONE
        }
    }
    
    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setMessage(R.string.history_confirm_clear)
            .setPositiveButton(R.string.history_yes) { _, _ ->
                historyManager.clearAll()
                loadHistory()
            }
            .setNegativeButton(R.string.history_no, null)
            .show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class HistoryAdapter(
    private val onDelete: (ReflectionEntry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {
    
    private var entries = listOf<ReflectionEntry>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    fun setData(newEntries: List<ReflectionEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(entries[position])
    }
    
    override fun getItemCount() = entries.size
    
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val sourceText: TextView = view.findViewById(R.id.sourceText)
        private val passageText: TextView = view.findViewById(R.id.passageText)
        private val contentText: TextView = view.findViewById(R.id.contentText)
        private val timeText: TextView = view.findViewById(R.id.timeText)
        private val deleteButton: Button = view.findViewById(R.id.deleteButton)
        
        fun bind(entry: ReflectionEntry) {
            if (entry.passageSource.isNotBlank()) {
                sourceText.visibility = View.VISIBLE
                sourceText.text = "《${entry.passageSource}》"
            } else {
                sourceText.visibility = View.GONE
            }
            if (entry.passageText.isNotBlank()) {
                passageText.visibility = View.VISIBLE
                passageText.text = entry.passageText
            } else {
                passageText.visibility = View.GONE
            }
            contentText.text = entry.content
            timeText.text = dateFormat.format(Date(entry.timestamp))
            deleteButton.setOnClickListener {
                onDelete(entry)
            }
        }
    }
}
