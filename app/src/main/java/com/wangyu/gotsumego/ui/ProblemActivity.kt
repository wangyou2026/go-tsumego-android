package com.wangyu.gotsumego.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.wangyu.gotsumego.R
import com.wangyu.gotsumego.TsumegoApp
import com.wangyu.gotsumego.data.*
import com.wangyu.gotsumego.databinding.ActivityProblemBinding
import com.wangyu.gotsumego.util.GoBoard

class ProblemActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityProblemBinding
    private val repository by lazy { TsumegoApp.instance.repository }
    
    private var filterBook: String? = null
    private var problemList: List<Problem> = emptyList()
    private var currentIndex: Int = 0
    
    // 当前棋盘状态
    private var currentBoardString: String = ""
    private var currentSolutionIndex: Int = 0  // 当前解答到第几步
    private var isSolved: Boolean = false
    
    companion object {
        const val EXTRA_BOOK = "extra_book"
        const val EXTRA_TITLE = "extra_title"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProblemBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        filterBook = intent.getStringExtra(EXTRA_BOOK)
        binding.tvTitle.text = intent.getStringExtra(EXTRA_TITLE) ?: "做题"
        
        loadProblems()
        setupViews()
    }
    
    private fun loadProblems() {
        problemList = if (filterBook != null) {
            repository.getProblemsByBook(filterBook!!)
        } else {
            repository.getAllProblems()
        }
    }
    
    private fun setupViews() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnReset.setOnClickListener { resetCurrentProblem() }
        binding.btnPrev.setOnClickListener { showPreviousProblem() }
        binding.btnNext.setOnClickListener { showNextProblem() }
        binding.btnHint.setOnClickListener { showHint() }
        
        binding.boardView.onStoneClickListener = { index -> handleStoneClick(index) }
        
        showCurrentProblem()
    }
    
    private fun showCurrentProblem() {
        if (problemList.isEmpty()) {
            binding.tvProblemNumber.text = "暂无题目"
            return
        }
        
        val problem = problemList[currentIndex]
        
        // 显示题目信息
        binding.tvProblemNumber.text = "第 ${currentIndex + 1}/${problemList.size} 题"
        binding.tvDifficulty.text = problem.difficultyName
        binding.tvBoardSize.text = "${problem.boardSize}路"
        binding.tvToPlay.text = if (problem.toPlay == StoneColor.WHITE) "白先" else "黑先"
        
        // 初始化棋盘
        currentBoardString = problem.toBoardString()
        binding.boardView.boardSize = problem.boardSize
        binding.boardView.currentPlayer = problem.toPlay
        binding.boardView.updateBoard(currentBoardString)
        
        // 重置状态
        currentSolutionIndex = 0
        isSolved = false
        binding.tvFeedback.visibility = View.GONE
        binding.tvHint.visibility = View.GONE
        
        // 更新按钮状态
        binding.btnPrev.isEnabled = currentIndex > 0
        binding.btnNext.isEnabled = currentIndex < problemList.size - 1
        
        // 显示解答步数
        val moveCount = problem.solutionMoves.size
        if (moveCount > 0) {
            binding.btnHint.text = "提示 (${moveCount}步)"
        } else {
            binding.btnHint.text = "提示"
        }
    }
    
    private fun handleStoneClick(index: Int) {
        if (isSolved) return
        
        val problem = problemList[currentIndex]
        val solutionMoves = problem.solutionMoves
        
        if (solutionMoves.isEmpty()) {
            Toast.makeText(this, "无解答数据", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentSolutionIndex >= solutionMoves.size) {
            return
        }
        
        // 检查是否是正确的位置
        val expectedMove = solutionMoves[currentSolutionIndex]
        val expectedIndex = expectedMove.toIndex(problem.boardSize)
        
        if (index == expectedIndex) {
            // 正确！放置棋子
            placeStone(index, problem.boardSize, expectedMove.color)
            currentSolutionIndex++
            
            // 检查是否完成
            if (currentSolutionIndex >= solutionMoves.size) {
                isSolved = true
                showSuccess()
            } else {
                // 如果下一步是对手（电脑），自动下
                val nextMove = solutionMoves[currentSolutionIndex]
                val nextIsOpponent = nextMove.color != problem.toPlay
                
                if (nextIsOpponent && currentSolutionIndex < solutionMoves.size) {
                    // 延迟后自动下对手的棋
                    binding.boardView.postDelayed({
                        autoPlayOpponent()
                    }, 500)
                } else {
                    showFeedback("正确！继续", true)
                }
            }
        } else {
            // 错误
            showFeedback("错误，重试", false)
        }
    }
    
    private fun autoPlayOpponent() {
        if (currentSolutionIndex >= solutionMoves.size) return
        
        val problem = problemList[currentIndex]
        val move = solutionMoves[currentSolutionIndex]
        val index = move.toIndex(problem.boardSize)
        
        if (GoBoard.isEmptyAt(currentBoardString, index)) {
            placeStone(index, problem.boardSize, move.color)
            currentSolutionIndex++
            
            // 继续检查是否还有对手的棋要下
            if (currentSolutionIndex < solutionMoves.size) {
                val nextMove = solutionMoves[currentSolutionIndex]
                val nextIsOpponent = nextMove.color != problem.toPlay
                
                if (nextIsOpponent) {
                    binding.boardView.postDelayed({
                        autoPlayOpponent()
                    }, 300)
                }
            } else {
                isSolved = true
                showSuccess()
            }
        }
    }
    
    private val solutionMoves: List<SolutionMove>
        get() = problemList[currentIndex].solutionMoves
    
    private fun placeStone(index: Int, boardSize: Int, color: StoneColor) {
        currentBoardString = GoBoard.placeStone(currentBoardString, index, color, boardSize)
        binding.boardView.lastMoveIndex = index
        binding.boardView.currentPlayer = if (color == StoneColor.BLACK) StoneColor.WHITE else StoneColor.BLACK
        binding.boardView.updateBoard(currentBoardString, index)
    }
    
    private fun showSuccess() {
        binding.tvFeedback.text = "完成！"
        binding.tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.correct_green))
        binding.tvFeedback.visibility = View.VISIBLE
        
        // 显示解答注释
        val problem = problemList[currentIndex]
        if (!problem.solutionComment.isNullOrBlank()) {
            binding.tvHint.text = problem.solutionComment
            binding.tvHint.visibility = View.VISIBLE
        }
        
        binding.btnHint.text = "下一题"
    }
    
    private fun showFeedback(message: String, isCorrect: Boolean) {
        binding.tvFeedback.text = message
        binding.tvFeedback.setTextColor(
            ContextCompat.getColor(this, 
                if (isCorrect) R.color.correct_green else R.color.incorrect_red
            )
        )
        binding.tvFeedback.visibility = View.VISIBLE
        
        if (!isCorrect) {
            binding.tvFeedback.postDelayed({
                binding.tvFeedback.visibility = View.GONE
            }, 1500)
        }
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
        if (isSolved && currentIndex < problemList.size - 1) {
            currentIndex++
            showCurrentProblem()
        } else if (currentIndex < problemList.size - 1) {
            currentIndex++
            showCurrentProblem()
        }
    }
    
    private fun showHint() {
        if (isSolved) {
            showNextProblem()
            return
        }
        
        val problem = problemList[currentIndex]
        if (currentSolutionIndex < solutionMoves.size) {
            val move = solutionMoves[currentSolutionIndex]
            val index = move.toIndex(problem.boardSize)
            
            // 高亮提示位置
            binding.boardView.hintIndex = index
            binding.boardView.showHint = true
            binding.boardView.invalidate()
            
            // 3秒后隐藏
            binding.boardView.postDelayed({
                binding.boardView.showHint = false
                binding.boardView.invalidate()
            }, 3000)
        }
    }
}
