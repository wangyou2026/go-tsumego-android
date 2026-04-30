package com.wangyu.gotsumego.ui

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
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
    
    // 试下模式状态
    private var isTrialMode: Boolean = false
    private var trialBoardString: String = ""
    private var trialStoneIndices: MutableSet<Int> = mutableSetOf()
    private var trialCurrentPlayer: StoneColor = StoneColor.BLACK
    
    // 设置相关
    private lateinit var prefs: SharedPreferences
    private var soundEnabled: Boolean = true
    private var trialModeEnabled: Boolean = true  // 默认开启试下模式
    
    // 音效相关
    private var soundPool: SoundPool? = null
    private var stoneSoundId: Int = 0
    
    companion object {
        const val EXTRA_BOOK = "extra_book"
        const val EXTRA_TITLE = "extra_title"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProblemBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 加载设置
        prefs = getSharedPreferences("go_tsumego_settings", Context.MODE_PRIVATE)
        soundEnabled = prefs.getBoolean("sound_enabled", true)
        trialModeEnabled = prefs.getBoolean("trial_mode_enabled", true)
        
        // 初始化音效
        initSoundPool()
        
        filterBook = intent.getStringExtra(EXTRA_BOOK)
        binding.tvTitle.text = intent.getStringExtra(EXTRA_TITLE) ?: "围棋死活题"
        
        loadProblems()
        setupViews()
    }
    
    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(audioAttributes)
            .build()
        
        // 加载落子音效
        try {
            stoneSoundId = soundPool?.load(this, R.raw.stone_place, 1) ?: 0
        } catch (e: Exception) {
            // 音效文件可能不存在
            stoneSoundId = 0
        }
    }
    
    private fun playStoneSound() {
        if (soundEnabled && stoneSoundId > 0) {
            soundPool?.play(stoneSoundId, 0.8f, 0.8f, 1, 0, 1.0f)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        soundPool?.release()
        soundPool = null
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
        binding.btnSettings.setOnClickListener { openSettings() }
        binding.btnReset.setOnClickListener { handleReset() }
        binding.btnPrev.setOnClickListener { showPreviousProblem() }
        binding.btnNext.setOnClickListener { showNextProblem() }
        binding.btnHint.setOnClickListener { showHint() }
        binding.btnExitTrial.setOnClickListener { exitTrialAndReset() }
        
        binding.boardView.onStoneClickListener = { index -> handleStoneClick(index) }
        
        showCurrentProblem()
    }
    
    private fun openSettings() {
        val intent = android.content.Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }
    
    private fun exitTrialAndReset() {
        exitTrialMode()
        showCurrentProblem()
    }
    
    private fun showCurrentProblem() {
        if (problemList.isEmpty()) {
            binding.tvProblemNumber.text = "暂无题目"
            binding.tvToPlay.visibility = View.GONE
            return
        }
        
        val problem = problemList[currentIndex]
        
        // 显示题目信息 - 新格式
        binding.tvProblemNumber.text = "${currentIndex + 1} / ${problemList.size}"
        binding.tvToPlay.text = if (problem.toPlay == StoneColor.WHITE) "白先" else "黑先"
        binding.tvToPlay.visibility = View.VISIBLE
        
        // 始终使用完整棋盘数据
        currentBoardString = problem.toBoardString()
        binding.boardView.boardSize = problem.boardSize
        
        // 设置局部放大区域
        binding.boardView.setZoomArea(
            problem.zoomMinCol, problem.zoomMaxCol,
            problem.zoomMinRow, problem.zoomMaxRow
        )
        
        binding.boardView.currentPlayer = problem.toPlay
        binding.boardView.updateBoard(currentBoardString)
        
        // 重置状态
        currentSolutionIndex = 0
        isSolved = false
        
        // 退出试下模式
        exitTrialMode()
        
        binding.tvFeedback.visibility = View.GONE
        binding.tvHint.visibility = View.GONE
        
        // 更新按钮状态
        binding.btnPrev.isEnabled = currentIndex > 0
        binding.btnNext.isEnabled = currentIndex < problemList.size - 1
        
        val moveCount = problem.solutionMoves.size
        binding.btnHint.text = if (moveCount > 0) "提示($moveCount)" else "提示"
    }
    
    private fun handleStoneClick(index: Int) {
        // 试下模式下自由落子
        if (isTrialMode) {
            handleTrialStoneClick(index)
            return
        }
        
        if (isSolved) return
        
        val problem = problemList[currentIndex]
        val solutionMoves = problem.solutionMoves
        
        if (solutionMoves.isEmpty()) {
            Toast.makeText(this, "无解答数据", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentSolutionIndex >= solutionMoves.size) return
        
        val expectedMove = solutionMoves[currentSolutionIndex]
        
        // 直接使用棋盘坐标的index
        val expectedIndex = expectedMove.toIndex(problem.boardSize)
        
        if (index == expectedIndex) {
            placeStone(index, problem, expectedMove.color)
            currentSolutionIndex++
            playStoneSound()
            
            if (currentSolutionIndex >= solutionMoves.size) {
                isSolved = true
                showSuccess()
            } else {
                val nextMove = solutionMoves[currentSolutionIndex]
                val nextIsOpponent = nextMove.color != problem.toPlay
                
                if (nextIsOpponent && currentSolutionIndex < solutionMoves.size) {
                    binding.boardView.postDelayed({ autoPlayOpponent() }, 500)
                } else {
                    showFeedback("正确!", true)
                }
            }
        } else {
            // 点错时进入试下模式
            if (trialModeEnabled) {
                enterTrialMode()
                showFeedback("试下中...", false)
            } else {
                showFeedback("不正确", false)
            }
        }
    }
    
    // 试下模式处理
    private fun enterTrialMode() {
        val problem = problemList[currentIndex]
        
        isTrialMode = true
        trialBoardString = currentBoardString
        trialStoneIndices.clear()
        trialCurrentPlayer = problem.toPlay
        
        // 更新UI
        binding.boardView.trialModeEnabled = true
        binding.boardView.trialStoneIndices = emptySet()
        binding.tvTrialMode.visibility = View.VISIBLE
        binding.btnExitTrial.visibility = View.VISIBLE
        binding.btnReset.text = "重置题目"
    }
    
    private fun exitTrialMode() {
        isTrialMode = false
        trialBoardString = ""
        trialStoneIndices.clear()
        trialCurrentPlayer = StoneColor.BLACK
        
        // 更新UI
        binding.boardView.trialModeEnabled = false
        binding.boardView.trialStoneIndices = emptySet()
        binding.tvTrialMode.visibility = View.GONE
        binding.btnExitTrial.visibility = View.GONE
        binding.btnReset.text = getString(R.string.reset)
    }
    
    private fun handleTrialStoneClick(index: Int) {
        val problem = problemList[currentIndex]
        
        // 检查位置是否已有棋子
        if (!GoBoard.isEmptyAt(trialBoardString, index)) {
            return
        }
        
        // 放置棋子
        val newBoard = GoBoard.placeStone(trialBoardString, index, trialCurrentPlayer, problem.boardSize)
        
        if (newBoard != trialBoardString) {
            // 成功落子
            trialBoardString = newBoard
            trialStoneIndices.add(index)
            trialCurrentPlayer = if (trialCurrentPlayer == StoneColor.BLACK) StoneColor.WHITE else StoneColor.BLACK
            
            // 更新棋盘显示
            binding.boardView.boardString = trialBoardString
            binding.boardView.lastMoveIndex = index
            binding.boardView.currentPlayer = trialCurrentPlayer
            binding.boardView.trialStoneIndices = trialStoneIndices.toSet()
            binding.boardView.invalidate()
            
            playStoneSound()
        }
    }
    
    private fun handleReset() {
        if (isTrialMode) {
            // 退出试下模式，回到题目初始状态
            exitTrialMode()
            showCurrentProblem()
        } else {
            // 普通重置
            resetCurrentProblem()
        }
    }
    
    private fun autoPlayOpponent() {
        if (currentSolutionIndex >= solutionMoves.size) return
        
        val problem = problemList[currentIndex]
        val move = solutionMoves[currentSolutionIndex]
        
        // 直接使用棋盘坐标的index
        val index = move.toIndex(problem.boardSize)
        
        if (GoBoard.isEmptyAt(currentBoardString, index)) {
            placeStone(index, problem, move.color)
            currentSolutionIndex++
            playStoneSound()
            
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
        currentBoardString = GoBoard.placeStone(currentBoardString, index, color, problem.boardSize)
        binding.boardView.lastMoveIndex = index
        binding.boardView.currentPlayer = if (color == StoneColor.BLACK) StoneColor.WHITE else StoneColor.BLACK
        binding.boardView.updateBoard(currentBoardString, index)
    }
    
    private fun showSuccess() {
        binding.tvFeedback.text = getString(R.string.problem_solved)
        binding.tvFeedback.setTextColor(ContextCompat.getColor(this, R.color.correct_green))
        binding.tvFeedback.visibility = View.VISIBLE
        
        val problem = problemList[currentIndex]
        if (!problem.solutionComment.isNullOrBlank()) {
            binding.tvHint.text = problem.solutionComment
            binding.tvHint.visibility = View.VISIBLE
        }
        
        binding.btnHint.text = getString(R.string.next_problem)
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
            
            val index = move.toIndex(problem.boardSize)
            
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
