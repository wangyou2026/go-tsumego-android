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
    private var currentSolutionIndex: Int = 0
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
            binding.tvToPlay.visibility = View.GONE
            return
        }
        
        val problem = problemList[currentIndex]
        
        // 显示题目信息
        binding.tvProblemNumber.text = "第 ${currentIndex + 1}/${problemList.size} 题"
        binding.tvToPlay.text = if (problem.toPlay == StoneColor.WHITE) "白先" else "黑先"
        binding.tvToPlay.visibility = View.VISIBLE
        
        // 判断是否使用裁剪模式
        val useCrop = problem.shouldCrop
        
        if (useCrop) {
            // 裁剪模式：显示局部棋盘
            currentBoardString = problem.toCroppedBoardString()
            binding.boardView.boardSize = problem.cropSize
        } else {
            // 标准模式：显示完整棋盘
            currentBoardString = problem.toBoardString()
            binding.boardView.boardSize = problem.boardSize
        }
        
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
        
        val moveCount = problem.solutionMoves.size
        binding.btnHint.text = if (moveCount > 0) "提示($moveCount)" else "提示"
    }
    
    private fun handleStoneClick(index: Int) {
        if (isSolved) return
        
        val problem = problemList[currentIndex]
        val solutionMoves = problem.solutionMoves
        
        if (solutionMoves.isEmpty()) {
            Toast.makeText(this, "无解答数据", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentSolutionIndex >= solutionMoves.size) return
        
        val expectedMove = solutionMoves[currentSolutionIndex]
        
        // 计算期望位置在当前棋盘上的index
        val expectedIndex = if (problem.shouldCrop) {
            // 裁剪模式：转换坐标
            val (croppedCol, croppedRow) = problem.globalToCropped(expectedMove.col, expectedMove.row)
            croppedRow * problem.cropSize + croppedCol
        } else {
            expectedMove.toIndex(problem.boardSize)
        }
        
        if (index == expectedIndex) {
            placeStone(index, problem, expectedMove.color)
            currentSolutionIndex++
            
            if (currentSolutionIndex >= solutionMoves.size) {
                isSolved = true
                showSuccess()
            } else {
                val nextMove = solutionMoves[currentSolutionIndex]
                val nextIsOpponent = nextMove.color != problem.toPlay
                
                if (nextIsOpponent && currentSolutionIndex < solutionMoves.size) {
                    binding.boardView.postDelayed({ autoPlayOpponent() }, 500)
                } else {
                    showFeedback("正确！", true)
                }
            }
        } else {
            showFeedback("错误", false)
        }
    }
    
    private fun autoPlayOpponent() {
        if (currentSolutionIndex >= solutionMoves.size) return
        
        val problem = problemList[currentIndex]
        val move = solutionMoves[currentSolutionIndex]
        
        // 计算在当前棋盘上的index
        val index = if (problem.shouldCrop) {
            val (croppedCol, croppedRow) = problem.globalToCropped(move.col, move.row)
            croppedRow * problem.cropSize + croppedCol
        } else {
            move.toIndex(problem.boardSize)
        }
        
        val currentBoardSize = if (problem.shouldCrop) problem.cropSize else problem.boardSize
        
        if (GoBoard.isEmptyAt(currentBoardString, index)) {
            placeStone(index, problem, move.color)
            currentSolutionIndex++
            
            if (currentSolutionIndex < solutionMoves.size) {
                val nextMove = solutionMoves[currentSolutionIndex]
                val nextIsOpponent = nextMove.color != problem.toPlay
                
                if (nextIsOpponent) {
                    binding.boardView.postDelayed({ autoPlayOpponent() }, 300)
                }
            } else {
                isSolved = true
                showSuccess()
            }
        }
    }
    
    private val solutionMoves: List<SolutionMove>
        get() = problemList[currentIndex].solutionMoves
    
    private fun placeStone(index: Int, problem: Problem, color: StoneColor) {
        val currentBoardSize = if (problem.shouldCrop) problem.cropSize else problem.boardSize
        currentBoardString = GoBoard.placeStone(currentBoardString, index, color, currentBoardSize)
        binding.boardView.lastMoveIndex = index
        binding.boardView.currentPlayer = if (color == StoneColor.BLACK) StoneColor.WHITE else StoneColor.BLACK
        binding.boardView.updateBoard(currentBoardString, index)
    }
    
    private fun showSuccess() {
        binding.tvFeedback.text = "完成！"
        binding.tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.correct_green))
        binding.tvFeedback.visibility = View.VISIBLE
        
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
        if (currentIndex < problemList.size - 1) {
            currentIndex++
            showCurrentProblem()
        }
    }
    
    private fun showHint() {
        if (isSolved) {
            showNextProblem()
            return
        }
        
        if (currentSolutionIndex < solutionMoves.size) {
            val problem = problemList[currentIndex]
            val move = solutionMoves[currentSolutionIndex]
            
            val index = if (problem.shouldCrop) {
                val (croppedCol, croppedRow) = problem.globalToCropped(move.col, move.row)
                croppedRow * problem.cropSize + croppedCol
            } else {
                move.toIndex(problem.boardSize)
            }
            
            binding.boardView.hintIndex = index
            binding.boardView.showHint = true
            binding.boardView.invalidate()
            
            binding.boardView.postDelayed({
                binding.boardView.showHint = false
                binding.boardView.invalidate()
            }, 3000)
        }
    }
}
