package com.wangyu.gotsumego.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wangyu.gotsumego.R
import com.wangyu.gotsumego.TsumegoApp
import com.wangyu.gotsumego.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val repository by lazy { TsumegoApp.instance.repository }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
    }
    
    private fun setupViews() {
        // 设置按钮
        binding.btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
        
        // 全部题目
        val total = repository.getTotalCount()
        binding.tvAllCount.text = "${String.format("%,d", total)} 道题目"
        binding.cardAllProblems.setOnClickListener {
            openProblemList(null, "全部题目")
        }
        
        // 书籍分类网格
        val bookStats = repository.getBookStatistics()
        binding.rvDifficulties.layoutManager = GridLayoutManager(this, 2)
        binding.rvDifficulties.adapter = BookAdapter(bookStats) { book ->
            openProblemList(book, book)
        }
    }
    
    private fun openProblemList(book: String?, title: String) {
        val intent = Intent(this, ProblemActivity::class.java).apply {
            putExtra(ProblemActivity.EXTRA_BOOK, book)
            putExtra(ProblemActivity.EXTRA_TITLE, title)
        }
        startActivity(intent)
    }
    
    inner class BookAdapter(
        private val bookStats: Map<String, Int>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<BookAdapter.ViewHolder>() {
        
        private val books = bookStats.entries.toList()
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvDifficultyTitle)
            val tvCount: TextView = itemView.findViewById(R.id.tvProblemCount)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_difficulty, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (book, count) = books[position]
            holder.tvTitle.text = book
            holder.tvCount.text = "${String.format("%,d", count)} 题"
            holder.itemView.setOnClickListener { onItemClick(book) }
        }
        
        override fun getItemCount(): Int = books.size
    }
}
