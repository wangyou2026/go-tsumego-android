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
import com.wangyu.gotsumego.databinding.ActivityProblemBinding
import com.wangyu.gotsumego.util.GoBoard

/**
 * 做题界面Activity
 */
class ProblemActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProblemBinding
    private val repository by lazy { TsumegoApp.instance.repository }
    
    // 当前筛选条件
    private var filterType: ProblemType? = null
    private var filterDifficulty: Int? = null
    private var filterBook: String? = null
    
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
        const val EXTRA_BOOK = "extra_book"
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
        filterBook = intent.getStringExtra(EXTRA_BOOK)
        
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "做题"
        binding.tvTitle.text = title
        
        currentIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
    }
    
    private fun loadProblems() {
        problemList = when {
            filterBook != null -> repository.getProblemsByBook(filterBook!!)
            filterType != null && filterDifficulty != null -> 
                repository.getProblemsByTypeAndDifficulty(filterType!!, filterDifficulty!!)
            filterType != null -> repository.getProblemsByType(filterType!!)
            filterDifficulty != null -> repository.getProblemsByDifficulty(filterDifficulty!!)
            else -> repository.getAllProblems()
        }
        
        if (currentIndex >= problemList.size) {
            currentIndex = 0
        }
    }
    
    private fun setupViews() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnReset.setOnClickListener { resetCurrentProblem() }
        binding.btnPrev.setOnClickListener { showPreviousProblem() }
        binding.btnNext.setOnClickListener { showNextProblem() }
        binding.btnHint.setOnClickListener { toggleHint() }
        
        binding.boardView.onStoneClickListener = { index -> handleStoneClick(index) }
        
        showCurrentProblem()
    }
    
    private fun showCurrentProblem() {
        if (problemList.isEmpty()) {
            binding.tvProblemNumber.text = "暂无题目"
            return
        }
        
        val problem = problemList[currentIndex]
        
        binding.tvProblemNumber.text = "第 ${currentIndex + 1}/${problemList.size} 题"
        binding.tvDifficulty.text = "难度: ${problem.difficultyName}"
        binding.tvBoardSize.text = "${problem.boardSize}路"
        
        binding.tvToPlay.text = when (problem.toPlay) {
            StoneColor.BLACK -> "黑先"
            StoneColor.WHITE -> "白先"
            else -> ""
        }
        
        currentBoardString = problem.toBoardString()
        binding.boardView.boardSize = problem.boardSize
        binding.boardView.currentPlayer = problem.toPlay
        binding.boardView.correctMoveIndex = problem.firstCorrectMove?.toIndex(problem.boardSize) ?: -1
        binding.boardView.showCorrectMove = false
        binding.boardView.updateBoard(currentBoardString)
        
        isSolved = false
        currentMoveIndex = -1
        binding.tvFeedback.visibility = View.GONE
        
        binding.btnPrev.isEnabled = currentIndex > 0
        binding.btnNext.isEnabled = currentIndex < problemList.size - 1
    }
    
    private fun handleStoneClick(index: Int) {
        if (isSolved) return
        
        val problem = problemList[currentIndex]
        
        if (!GoBoard.isEmptyAt(currentBoardString, index)) return
        
        val correctIndex = problem.firstCorrectMove?.toIndex(problem.boardSize) ?: return
        
        if (index == correctIndex) {
            handleCorrectAnswer(index, problem)
        } else {
            handleIncorrectAnswer()
        }
    }
    
    private fun handleCorrectAnswer(index: Int, problem: Problem) {
        isSolved = true
        currentMoveIndex = index
        
        val nextPlayer = if (problem.toPlay == StoneColor.BLACK) StoneColor.WHITE else StoneColor.BLACK
        currentBoardString = GoBoard.placeStone(currentBoardString, index, problem.toPlay, problem.boardSize)
        
        binding.boardView.currentPlayer = nextPlayer
        binding.boardView.lastMoveIndex = index
        binding.boardView.updateBoard(currentBoardString, index)
        
        binding.tvFeedback.text = "正确！"
        binding.tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.correct_green))
        binding.tvFeedback.visibility = View.VISIBLE
        
        binding.btnHint.text = "下一题"
    }
    
    private fun handleIncorrectAnswer() {
        binding.tvFeedback.text = "错误，再试试"
        binding.tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.incorrect_red))
        binding.tvFeedback.visibility = View.VISIBLE
        
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
            showNextProblem()
            binding.btnHint.text = "提示"
        } else {
            binding.boardView.showCorrectMove = !binding.boardView.showCorrectMove
            binding.boardView.invalidate()
            
            if (binding.boardView.showCorrectMove) {
                binding.tvHint.visibility = View.VISIBLE
            } else {
                binding.tvHint.visibility = View.GONE
            }
        }
    }
}
