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
 * 显示题目分类和难度选择
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
        setupDifficultyList()
    }
    
    private fun setupClickListeners() {
        // 全部题目
        binding.cardAllProblems.setOnClickListener {
            openProblemList(null, "全部题目")
        }
        
        // 死活题
        binding.cardLifeDeath.setOnClickListener {
            openProblemList(ProblemType.LIFE_DEATH, getString(R.string.life_death))
        }
        
        // 手筋题
        binding.cardTesuji.setOnClickListener {
            openProblemList(ProblemType.TESUJI, getString(R.string.tesuji))
        }
        
        // 官子题
        binding.cardYose.setOnClickListener {
            openProblemList(ProblemType.YOSE, getString(R.string.yose))
        }
        
        // 吃子题
        binding.cardCapture.setOnClickListener {
            openProblemList(ProblemType.CAPTURE, getString(R.string.capture))
        }
    }
    
    private fun loadStatistics() {
        val total = repository.getTotalCount()
        binding.tvAllCount.text = getString(R.string.total_problems, total)
        
        val lifeDeathCount = repository.getProblemCountByType(ProblemType.LIFE_DEATH)
        binding.tvLifeDeathCount.text = getString(R.string.total_problems, lifeDeathCount)
        
        val tesujiCount = repository.getProblemCountByType(ProblemType.TESUJI)
        binding.tvTesujiCount.text = getString(R.string.total_problems, tesujiCount)
        
        val yoseCount = repository.getProblemCountByType(ProblemType.YOSE)
        binding.tvYoseCount.text = getString(R.string.total_problems, yoseCount)
        
        val captureCount = repository.getProblemCountByType(ProblemType.CAPTURE)
        binding.tvCaptureCount.text = getString(R.string.total_problems, captureCount)
    }
    
    private fun setupDifficultyList() {
        val difficulties = repository.getDifficultyLevels()
        
        binding.rvDifficulties.layoutManager = LinearLayoutManager(this)
        binding.rvDifficulties.adapter = DifficultyAdapter(difficulties) { difficulty ->
            openProblemListByDifficulty(difficulty)
        }
    }
    
    private fun openProblemList(type: ProblemType?, title: String) {
        val intent = Intent(this, ProblemActivity::class.java).apply {
            putExtra(ProblemActivity.EXTRA_TYPE, type?.key)
            putExtra(ProblemActivity.EXTRA_TITLE, title)
        }
        startActivity(intent)
    }
    
    private fun openProblemListByDifficulty(difficulty: Int) {
        val intent = Intent(this, ProblemActivity::class.java).apply {
            putExtra(ProblemActivity.EXTRA_DIFFICULTY, difficulty)
            putExtra(ProblemActivity.EXTRA_TITLE, "难度 $difficulty")
        }
        startActivity(intent)
    }
    
    /**
     * 难度列表适配器
     */
    inner class DifficultyAdapter(
        private val difficulties: List<Int>,
        private val onItemClick: (Int) -> Unit
    ) : RecyclerView.Adapter<DifficultyAdapter.ViewHolder>() {
        
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
            val difficulty = difficulties[position]
            holder.tvLevel.text = difficulty.toString()
            holder.tvTitle.text = "难度 $difficulty"
            holder.tvCount.text = getString(
                R.string.total_problems,
                repository.getProblemCountByDifficulty(difficulty)
            )
            holder.itemView.setOnClickListener { onItemClick(difficulty) }
        }
        
        override fun getItemCount(): Int = difficulties.size
    }
}
