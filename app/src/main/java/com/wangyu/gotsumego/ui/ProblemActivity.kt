package com.wangyu.gotsumego.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.wangyu.gotsumego.R
import com.wangyu.gotsumego.TsumegoApp
import com.wangyu.gotsumego.data.Problem
import com.wangyu.gotsumego.data.ProblemType
import com.wangyu.gotsumego.data.StoneColor
import com.wangyu.gotsumego.data.toProblem
import com.wangyu.gotsumego.databinding.ActivityProblemBinding
import com.wangyu.gotsumego.util.GoBoard
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 做题界面Activity
 * 显示题目、接收用户落子、判断答案
 */
class ProblemActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProblemBinding
    private val repository by lazy { TsumegoApp.instance.repository }
    
    // 当前筛选条件
    private var filterType: ProblemType? = null
    private var filterDifficulty: Int? = null
    
    // 当前题目列表和索引
    private var problemList: List<Problem> = emptyList()
    private var currentIndex: Int = 0
    
    // 当前棋盘状态
    private var currentBoardString: String = ""
    private var currentMoveIndex: Int = -1
    private var isSolved: Boolean = false
    
    companion object {
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_DIFFICULTY = "extra_difficulty"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_START_INDEX = "extra_start_index"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProblemBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        parseIntent()
        loadProblems()
        setupViews()
    }
    
    private fun parseIntent() {
        val typeKey = intent.getStringExtra(EXTRA_TYPE)
        filterType = typeKey?.let { ProblemType.fromKey(it) }
        filterDifficulty = intent.getIntExtra(EXTRA_DIFFICULTY, -1).takeIf { it > 0 }
        
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "做题"
        binding.tvTitle.text = title
        
        currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
    }
    
    private fun loadProblems() {
        // 根据筛选条件加载题目
        problemList = when {
            filterType != null && filterDifficulty != null -> {
                repository.getProblemsByTypeAndDifficulty(filterType!!, filterDifficulty!!)
            }
            filterType != null -> {
                repository.getProblemsByType(filterType!!)
            }
            filterDifficulty != null -> {
                repository.getProblemsByDifficulty(filterDifficulty!!)
            }
            else -> {
                repository.getAllProblems()
            }
        }
        
        // 确保索引有效
        if (currentIndex >= problemList.size) {
            currentIndex = 0
        }
    }
    
    private fun setupViews() {
        // 返回按钮
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // 重置按钮
        binding.btnReset.setOnClickListener {
            resetCurrentProblem()
        }
        
        // 上一题
        binding.btnPrev.setOnClickListener {
            showPreviousProblem()
        }
        
        // 下一题
        binding.btnNext.setOnClickListener {
            showNextProblem()
        }
        
        // 提示按钮
        binding.btnHint.setOnClickListener {
            toggleHint()
        }
        
        // 棋盘点击监听
        binding.boardView.onStoneClickListener = { index ->
            handleStoneClick(index)
        }
        
        // 显示第一题
        showCurrentProblem()
    }
    
    private fun showCurrentProblem() {
        if (problemList.isEmpty()) {
            binding.tvProblemNumber.text = "暂无题目"
            return
        }
        
        val problem = problemList[currentIndex]
        
        // 更新题目信息
        binding.tvProblemNumber.text = getString(R.string.problem_number, currentIndex + 1)
        binding.tvDifficulty.text = "${getString(R.string.difficulty)}: ${problem.difficulty}"
        binding.tvBoardSize.text = "${problem.boardSize}路"
        
        // 下棋方
        binding.tvToPlay.text = when (problem.toPlay) {
            StoneColor.BLACK -> getString(R.string.black_to_play)
            StoneColor.WHITE -> getString(R.string.white_to_play)
            else -> ""
        }
        
        // 初始化棋盘
        currentBoardString = problem.toBoardString()
        binding.boardView.boardSize = problem.boardSize
        binding.boardView.currentPlayer = problem.toPlay
        binding.boardView.correctMoveIndex = problem.firstCorrectMove?.toIndex(problem.boardSize) ?: -1
        binding.boardView.showCorrectMove = false
        binding.boardView.updateBoard(currentBoardString)
        
        // 重置状态
        isSolved = false
        currentMoveIndex = -1
        binding.tvFeedback.visibility = View.GONE
        
        // 重置按钮状态
        binding.btnPrev.isEnabled = currentIndex > 0
        binding.btnNext.isEnabled = currentIndex < problemList.size - 1
    }
    
    private fun handleStoneClick(index: Int) {
        if (isSolved) return
        
        val problem = problemList[currentIndex]
        
        // 检查位置是否为空
        if (!GoBoard.isEmptyAt(currentBoardString, index)) {
            return
        }
        
        // 检查是否是正确的位置
        val correctIndex = problem.firstCorrectMove?.toIndex(problem.boardSize) ?: return
        
        if (index == correctIndex) {
            // 正确！
            handleCorrectAnswer(index, problem)
        } else {
            // 错误
            handleIncorrectAnswer()
        }
    }
    
    private fun handleCorrectAnswer(index: Int, problem: Problem) {
        isSolved = true
        currentMoveIndex = index
        
        // 落子到棋盘
        val nextPlayer = if (problem.toPlay == StoneColor.BLACK) StoneColor.WHITE else StoneColor.BLACK
        currentBoardString = GoBoard.placeStone(currentBoardString, index, problem.toPlay, problem.boardSize)
        
        binding.boardView.currentPlayer = nextPlayer
        binding.boardView.lastMoveIndex = index
        binding.boardView.updateBoard(currentBoardString, index)
        
        // 显示反馈
        binding.tvFeedback.text = getString(R.string.correct)
        binding.tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.correct_green))
        binding.tvFeedback.visibility = View.VISIBLE
        
        // 更新提示按钮
        binding.btnHint.text = getString(R.string.next_problem)
    }
    
    private fun handleIncorrectAnswer() {
        binding.tvFeedback.text = getString(R.string.incorrect)
        binding.tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.incorrect_red))
        binding.tvFeedback.visibility = View.VISIBLE
        
        // 2秒后隐藏反馈
        binding.tvFeedback.postDelayed({
            binding.tvFeedback.visibility = View.GONE
        }, 2000)
    }
    
    private fun resetCurrentProblem() {
        showCurrentProblem()
    }
    
    private fun showPreviousProblem() {
        if (currentIndex > 0) {
            currentIndex--
            showCurrentProblem()
        }
    }
    
    private fun showNextProblem() {
        if (currentIndex < problemList.size - 1) {
            currentIndex++
            showCurrentProblem()
        }
    }
    
    private fun toggleHint() {
        if (isSolved) {
            // 如果已解决，直接跳到下一题
            showNextProblem()
            binding.btnHint.text = getString(R.string.show_hint)
        } else {
            // 切换提示显示
            binding.boardView.showCorrectMove = !binding.boardView.showCorrectMove
            binding.boardView.invalidate()
            
            val problem = problemList[currentIndex]
            if (binding.boardView.showCorrectMove) {
                binding.tvHint.text = problem.hint ?: "点击星位或关键点"
                binding.tvHint.visibility = View.VISIBLE
            } else {
                binding.tvHint.visibility = View.GONE
            }
        }
    }
}
