package com.wangyu.gotsumego.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wangyu.gotsumego.R
import com.wangyu.gotsumego.TsumegoApp
import com.wangyu.gotsumego.data.ProblemType
import com.wangyu.gotsumego.databinding.ActivityMainBinding

/**
 * 主界面Activity
 * 显示题目分类（按书籍）
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val repository by lazy { TsumegoApp.instance.repository }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
        loadStatistics()
        setupBookList()
    }
    
    private fun setupClickListeners() {
        // 全部题目
        binding.cardAllProblems.setOnClickListener {
            openProblemList(book = null, title = "全部题目")
        }
        
        // 死活题 - 保留兼容
        binding.cardLifeDeath.setOnClickListener {
            openProblemListByType(ProblemType.LIFE_DEATH, "死活题")
        }
        
        // 手筋题
        binding.cardTesuji.setOnClickListener {
            openProblemListByType(ProblemType.TESUJI, "手筋题")
        }
        
        // 官子题
        binding.cardYose.setOnClickListener {
            openProblemListByType(ProblemType.YOSE, "官子题")
        }
        
        // 吃子题
        binding.cardCapture.setOnClickListener {
            openProblemListByType(ProblemType.CAPTURE, "吃子题")
        }
    }
    
    private fun loadStatistics() {
        val total = repository.getTotalCount()
        binding.tvAllCount.text = "共${total}题"
        
        val lifeDeathCount = repository.getProblemCountByType(ProblemType.LIFE_DEATH)
        binding.tvLifeDeathCount.text = "共${lifeDeathCount}题"
        
        val tesujiCount = repository.getProblemCountByType(ProblemType.TESUJI)
        binding.tvTesujiCount.text = "共${tesujiCount}题"
        
        val yoseCount = repository.getProblemCountByType(ProblemType.YOSE)
        binding.tvYoseCount.text = "共${yoseCount}题"
        
        val captureCount = repository.getProblemCountByType(ProblemType.CAPTURE)
        binding.tvCaptureCount.text = "共${captureCount}题"
    }
    
    private fun setupBookList() {
        val bookStats = repository.getBookStatistics()
        
        binding.rvDifficulties.layoutManager = LinearLayoutManager(this)
        binding.rvDifficulties.adapter = BookAdapter(bookStats) { book ->
            openProblemList(book = book, title = book)
        }
    }
    
    private fun openProblemList(book: String?, title: String) {
        val intent = Intent(this, ProblemActivity::class.java).apply {
            putExtra(ProblemActivity.EXTRA_BOOK, book)
            putExtra(ProblemActivity.EXTRA_TITLE, title)
        }
        startActivity(intent)
    }
    
    private fun openProblemListByType(type: ProblemType, title: String) {
        val intent = Intent(this, ProblemActivity::class.java).apply {
            putExtra(ProblemActivity.EXTRA_TYPE, type.key)
            putExtra(ProblemActivity.EXTRA_TITLE, title)
        }
        startActivity(intent)
    }
    
    /**
     * 书籍列表适配器
     */
    inner class BookAdapter(
        private val bookStats: Map<String, Int>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<BookAdapter.ViewHolder>() {
        
        private val books = bookStats.entries.toList()
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvLevel: TextView = itemView.findViewById(R.id.tvDifficultyLevel)
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
            holder.tvLevel.text = "📚"
            holder.tvTitle.text = book
            holder.tvCount.text = "共${count}题"
            holder.itemView.setOnClickListener { onItemClick(book) }
        }
        
        override fun getItemCount(): Int = books.size
    }
}
