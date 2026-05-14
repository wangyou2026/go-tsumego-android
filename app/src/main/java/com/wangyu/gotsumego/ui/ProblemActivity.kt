package com.wangyu.gotsumego.ui

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.view.View
import android.animation.ObjectAnimator
import android.animation.AnimatorListenerAdapter
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
    private var currentBoardString: String = ""
    private var currentSolutionIndex: Int = 0
    private var isSolved: Boolean = false
    private var isAutoPlaying: Boolean = false
    private var isShowingAnswer: Boolean = false
    private var isTrialMode: Boolean = false
    private var trialBoardString: String = ""
    private var trialStoneIndices: MutableSet<Int> = mutableSetOf()
    private var trialCurrentPlayer: StoneColor = StoneColor.BLACK
    private lateinit var prefs: SharedPreferences
    private var soundEnabled: Boolean = true
    private var soundPool: SoundPool? = null
    private var stoneSoundId: Int = 0
    
    companion object {
        const val EXTRA_BOOK = "extra_book"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_RANDOM = "extra_random"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProblemBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("go_tsumego_settings", Context.MODE_PRIVATE)
        soundEnabled = prefs.getBoolean("sound_enabled", true)
        initSoundPool()
        filterBook = intent.getStringExtra(EXTRA_BOOK)
        val isRandom = intent.getBooleanExtra(EXTRA_RANDOM, false)
        binding.tvTitle.text = intent.getStringExtra(EXTRA_TITLE) ?: "围棋死活题"
        loadProblems(isRandom)
        setupViews()
    }
    
    private fun initSoundPool() {
        val attr = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        soundPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attr).build()
        try { stoneSoundId = soundPool?.load(this, R.raw.stone_place, 1) ?: 0 } catch (e: Exception) { stoneSoundId = 0 }
    }
    private fun playStoneSound() { if (soundEnabled && stoneSoundId > 0) soundPool?.play(stoneSoundId, 0.8f, 0.8f, 1, 0, 1.0f) }
    override fun onDestroy() { super.onDestroy(); soundPool?.release(); soundPool = null }
    
    private fun loadProblems(random: Boolean) {
        problemList = if (filterBook != null) repository.getProblemsByBook(filterBook!!) else repository.getAllProblems()
        if (random && problemList.isNotEmpty()) problemList = problemList.shuffled()
    }
    
    private fun setupViews() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnReset.setOnClickListener { exitTrialMode(); showCurrentProblem() }
        binding.btnPrev.setOnClickListener { if (currentIndex > 0) { currentIndex--; showCurrentProblem() } }
        binding.btnNext.setOnClickListener { if (currentIndex < problemList.size - 1) { currentIndex++; showCurrentProblem() } }
        binding.btnShowAnswer.setOnClickListener { showFullAnswer() }
        binding.btnExitTrial.setOnClickListener { exitTrialMode(); showCurrentProblem() }
        binding.boardView.onStoneClickListener = { index -> handleStoneClick(index) }
        showCurrentProblem()
    }
    
    private fun showFullAnswer() {
        if (problemList.isEmpty()) return
        val problem = problemList[currentIndex]
        val moves = problem.solutionMoves
        if (moves.isEmpty()) { Toast.makeText(this, "无答案数据", Toast.LENGTH_SHORT).show(); return }
        if (isShowingAnswer) { isShowingAnswer = false; isAutoPlaying = false; binding.btnShowAnswer.text = "显示答案"; updateProgress(0, 0); showCurrentProblem(); return }
        currentBoardString = problem.toBoardString()
        binding.boardView.currentPlayer = problem.toPlay
        binding.boardView.updateBoard(currentBoardString)
        currentSolutionIndex = 0; isShowingAnswer = true; isSolved = true; exitTrialMode()
        binding.btnShowAnswer.text = "停止播放"
        binding.tvFeedback.visibility = View.GONE
        updateProgress(0, moves.size)
        playNextAnswerStep()
    }
    
    private fun playNextAnswerStep() {
        if (!isShowingAnswer) return
        val problem = problemList.getOrNull(currentIndex) ?: return
        val moves = problem.solutionMoves
        if (currentSolutionIndex >= moves.size) {
            isShowingAnswer = false; isAutoPlaying = false; binding.btnShowAnswer.text = "显示答案"
            showFeedbackOverlay("正解", true)
            updateProgress(moves.size, moves.size)
            return
        }
        val move = moves[currentSolutionIndex]; val index = move.toIndex(13)
        val prev = currentBoardString
        currentBoardString = GoBoard.placeStone(currentBoardString, index, move.color, 13)
        if (currentBoardString != prev) {
            currentSolutionIndex++; playStoneSound()
            binding.boardView.lastMoveIndex = index
            binding.boardView.currentPlayer = if (move.color == StoneColor.BLACK) StoneColor.WHITE else StoneColor.BLACK
            binding.boardView.updateBoard(currentBoardString, index)
            updateProgress(currentSolutionIndex, moves.size)
        } else { currentSolutionIndex++ }
        binding.boardView.postDelayed({ playNextAnswerStep() }, 600)
    }
    
    private fun showCurrentProblem() {
        if (problemList.isEmpty()) { binding.tvProblemNumber.text = "暂无题目"; binding.tvToPlay.visibility = View.GONE; return }
        val problem = problemList[currentIndex]
        
        // 标题：书名 · 第N题
        val bookName = problem.book
        val titleText = "$bookName · 第${currentIndex + 1}题"
        binding.tvTitle.text = titleText
        
        // 题号信息
        binding.tvProblemNumber.text = "${currentIndex + 1} / ${problemList.size}"
        binding.tvToPlay.text = if (problem.toPlay == StoneColor.WHITE) "白先" else "黑先"
        binding.tvToPlay.visibility = View.VISIBLE
        
        // 步数提示
        val moveCount = problem.solutionMoves.size
        binding.tvMoveCount.text = "${moveCount}步"
        binding.tvMoveCount.visibility = if (moveCount > 0) View.VISIBLE else View.GONE
        
        // 隐藏进度
        binding.tvProgress.visibility = View.GONE
        
        currentBoardString = problem.toBoardString()
        binding.boardView.currentPlayer = problem.toPlay; binding.boardView.updateBoard(currentBoardString)
        currentSolutionIndex = 0; isSolved = false; isAutoPlaying = false; isShowingAnswer = false; exitTrialMode()
        binding.tvFeedback.visibility = View.GONE
        binding.tvFeedbackOverlay.visibility = View.GONE
        binding.btnShowAnswer.text = if (moveCount > 0) "显示答案" else "显示答案"
        binding.btnPrev.isEnabled = currentIndex > 0; binding.btnNext.isEnabled = currentIndex < problemList.size - 1
        binding.btnPrev.alpha = if (currentIndex > 0) 1.0f else 0.4f
        binding.btnNext.alpha = if (currentIndex < problemList.size - 1) 1.0f else 0.4f
    }
    
    private fun updateProgress(current: Int, total: Int) {
        if (total > 0 && isShowingAnswer) {
            binding.tvProgress.text = "$current/$total"
            binding.tvProgress.visibility = View.VISIBLE
            binding.tvMoveCount.visibility = View.GONE
        } else {
            binding.tvProgress.visibility = View.GONE
            val problem = problemList.getOrNull(currentIndex)
            if (problem != null) {
                binding.tvMoveCount.text = "${problem.solutionMoves.size}步"
                binding.tvMoveCount.visibility = if (problem.solutionMoves.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
    
    private fun handleStoneClick(index: Int) {
        if (isAutoPlaying || isShowingAnswer) return
        if (isTrialMode) { handleTrialClick(index); return }
        if (isSolved) return
        val problem = problemList[currentIndex]; val moves = problem.solutionMoves
        if (moves.isEmpty()) { Toast.makeText(this, "无解答数据", Toast.LENGTH_SHORT).show(); return }
        if (currentSolutionIndex >= moves.size) return
        val expected = moves[currentSolutionIndex]; val expectedIdx = expected.toIndex(13)
        if (index == expectedIdx) {
            val prev = currentBoardString; placeStone(index, problem, expected.color)
            if (currentBoardString != prev) {
                currentSolutionIndex++; playStoneSound()
                updateProgress(currentSolutionIndex, moves.size)
                if (currentSolutionIndex >= moves.size) { isSolved = true; showSuccess() }
                else { val next = moves[currentSolutionIndex]; if (next.color != problem.toPlay) binding.boardView.postDelayed({ autoPlayOpponent() }, 500) else showFeedbackOverlay("正确!", true) }
            } else enterTrialMode()
        } else { enterTrialMode(); showFeedbackOverlay("试下中...", false) }
    }
    
    private fun enterTrialMode() {
        val problem = problemList[currentIndex]
        isTrialMode = true; trialBoardString = currentBoardString; trialStoneIndices.clear(); trialCurrentPlayer = problem.toPlay
        binding.boardView.trialModeEnabled = true; binding.boardView.trialStoneIndices = emptySet()
        binding.tvTrialMode.visibility = View.VISIBLE; binding.btnExitTrial.visibility = View.VISIBLE
        Toast.makeText(this, "已进入试下模式，可自由落子", Toast.LENGTH_LONG).show()
    }
    
    private fun exitTrialMode() {
        isTrialMode = false; trialBoardString = ""; trialStoneIndices.clear()
        binding.boardView.trialModeEnabled = false; binding.boardView.trialStoneIndices = emptySet()
        binding.tvTrialMode.visibility = View.GONE; binding.btnExitTrial.visibility = View.GONE
    }
    
    private fun handleTrialClick(index: Int) {
        val problem = problemList[currentIndex]
        if (!GoBoard.isEmptyAt(trialBoardString, index)) return
        val newBoard = GoBoard.placeStone(trialBoardString, index, trialCurrentPlayer, 13)
        if (newBoard != trialBoardString) {
            trialBoardString = newBoard; trialStoneIndices.add(index)
            trialCurrentPlayer = if (trialCurrentPlayer == StoneColor.BLACK) StoneColor.WHITE else StoneColor.BLACK
            binding.boardView.boardString = trialBoardString; binding.boardView.lastMoveIndex = index
            binding.boardView.currentPlayer = trialCurrentPlayer; binding.boardView.trialStoneIndices = trialStoneIndices.toSet()
            binding.boardView.invalidate(); playStoneSound()
        }
    }
    
    private fun autoPlayOpponent() {
        val problem = problemList.getOrNull(currentIndex) ?: return
        if (currentSolutionIndex >= problem.solutionMoves.size) { isAutoPlaying = false; return }
        isAutoPlaying = true; playOpponentMove()
    }
    
    private fun playOpponentMove() {
        val problem = problemList.getOrNull(currentIndex) ?: run { isAutoPlaying = false; return }
        val moves = problem.solutionMoves
        if (currentSolutionIndex >= moves.size) { isSolved = true; isAutoPlaying = false; showSuccess(); return }
        val move = moves[currentSolutionIndex]; val index = move.toIndex(13); val prev = currentBoardString
        placeStone(index, problem, move.color)
        if (currentBoardString != prev) {
            currentSolutionIndex++; playStoneSound()
            updateProgress(currentSolutionIndex, moves.size)
            if (currentSolutionIndex >= moves.size) { isSolved = true; isAutoPlaying = false; showSuccess(); return }
            val next = moves[currentSolutionIndex]
            if (next.color != problem.toPlay) binding.boardView.postDelayed({ playOpponentMove() }, 300)
            else { isAutoPlaying = false; showFeedbackOverlay("正确!", true) }
        } else { isAutoPlaying = false; enterTrialMode() }
    }
    
    private fun placeStone(index: Int, problem: Problem, color: StoneColor) {
        currentBoardString = GoBoard.placeStone(currentBoardString, index, color, 13)
        binding.boardView.lastMoveIndex = index; binding.boardView.currentPlayer = if (color == StoneColor.BLACK) StoneColor.WHITE else StoneColor.BLACK
        binding.boardView.updateBoard(currentBoardString, index)
    }
    
    private fun showSuccess() {
        showFeedbackOverlay("正解！", true)
        binding.btnShowAnswer.text = "下一题"; binding.btnShowAnswer.setOnClickListener { if (currentIndex < problemList.size - 1) { currentIndex++; showCurrentProblem() }; binding.btnShowAnswer.setOnClickListener { showFullAnswer() } }
    }
    
    /**
     * 中央浮动反馈提示 - 更醒目的显示方式
     */
    private fun showFeedbackOverlay(msg: String, isCorrect: Boolean) {
        val overlay = binding.tvFeedbackOverlay
        overlay.text = msg
        overlay.setTextColor(ContextCompat.getColor(this, if (isCorrect) R.color.correct_green else R.color.incorrect_red))
        overlay.visibility = View.VISIBLE
        overlay.alpha = 1.0f
        overlay.scaleX = 0.5f; overlay.scaleY = 0.5f
        
        // 放大弹入动画
        val scaleX = ObjectAnimator.ofFloat(overlay, "scaleX", 0.5f, 1.1f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(overlay, "scaleY", 0.5f, 1.1f, 1.0f)
        scaleX.duration = 300; scaleY.duration = 300
        scaleX.start(); scaleY.start()
        
        // 延迟淡出
        overlay.postDelayed({
            val fadeOut = ObjectAnimator.ofFloat(overlay, "alpha", 1.0f, 0f)
            fadeOut.duration = 600
            fadeOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animation) {
                    overlay.visibility = View.GONE
                }
            })
            fadeOut.start()
        }, if (isCorrect) 1500 else 1000)
    }
    
    private fun showFeedback(msg: String, ok: Boolean) {
        showFeedbackOverlay(msg, ok)
    }
}
