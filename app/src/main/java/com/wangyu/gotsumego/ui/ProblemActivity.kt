package com.wangyu.gotsumego.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.view.View
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.AnimatorListenerAdapter
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.wangyu.gotsumego.R
import com.wangyu.gotsumego.TsumegoApp
import com.wangyu.gotsumego.data.*
import com.wangyu.gotsumego.databinding.ActivityProblemBinding
import com.wangyu.gotsumego.util.GoBoard
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

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
    
    // 悔棋历史：记录每步的 (boardString, lastMoveIndex)
    private var moveHistory: MutableList<Pair<String, Int>> = mutableListOf()
    // 答案手数映射：棋盘index → 手数编号
    private var answerMoveMap: MutableMap<Int, Int> = mutableMapOf()
    
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
        // Restore last viewed position (not for random mode)
        if (!random && filterBook != null) {
            val savedIndex = prefs.getInt("progress_$filterBook", 0)
            if (savedIndex in 0 until problemList.size) {
                currentIndex = savedIndex
            }
        }
    }
    
    private fun setupViews() {
        binding.btnBack.setOnClickListener { saveProgress(); finish() }
        binding.btnReset.setOnClickListener { exitTrialMode(); showCurrentProblem() }
        binding.btnPrev.setOnClickListener { if (currentIndex > 0) { currentIndex--; saveProgress(); showCurrentProblem() } }
        binding.btnNext.setOnClickListener { if (currentIndex < problemList.size - 1) { currentIndex++; saveProgress(); showCurrentProblem() } }
        binding.btnShowAnswer.setOnClickListener { showFullAnswer() }
        binding.btnUndo.setOnClickListener { handleUndo() }
        binding.btnTrial.setOnClickListener { 
            if (!isTrialMode) {
                enterTrialMode()
            } else {
                exitTrialMode()
                showCurrentProblem()
            }
        }
        binding.btnExitTrial.setOnClickListener { exitTrialMode(); showCurrentProblem() }
        binding.boardView.onStoneClickListener = { index -> handleStoneClick(index) }
        showCurrentProblem()
    }
    
    private fun saveProgress() {
        if (filterBook != null && currentIndex >= 0) {
            prefs.edit().putInt("progress_$filterBook", currentIndex).apply()
        }
    }
    
    override fun onPause() {
        super.onPause()
        saveProgress()
    }
    
    private fun showFullAnswer() {
        if (problemList.isEmpty()) return
        val problem = problemList[currentIndex]
        val moves = problem.solutionMoves
        if (moves.isEmpty()) { Toast.makeText(this, "无答案数据", Toast.LENGTH_SHORT).show(); return }
        if (isShowingAnswer) { isShowingAnswer = false; isAutoPlaying = false; binding.btnShowAnswer.text = "显示答案"; updateProgress(0, 0); answerMoveMap.clear(); binding.boardView.answerMoveIndices = emptyMap(); showCurrentProblem(); return }
        currentBoardString = problem.toBoardString()
        binding.boardView.currentPlayer = problem.toPlay
        binding.boardView.updateBoard(currentBoardString)
        currentSolutionIndex = 0; isShowingAnswer = true; isSolved = true; exitTrialMode()
        answerMoveMap.clear(); binding.boardView.answerMoveIndices = emptyMap()
        binding.btnShowAnswer.text = "停止播放"
        binding.tvFeedback.visibility = View.GONE
        updateUndoButton()
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
            // 记录答案手数
            answerMoveMap[index] = currentSolutionIndex
            binding.boardView.answerMoveIndices = answerMoveMap.toMap()
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
        moveHistory.clear(); answerMoveMap.clear(); binding.boardView.answerMoveIndices = emptyMap()
        binding.tvFeedback.visibility = View.GONE
        binding.tvFeedbackOverlay.visibility = View.GONE
        binding.btnShowAnswer.text = if (moveCount > 0) "显示答案" else "显示答案"
        binding.btnPrev.isEnabled = currentIndex > 0; binding.btnNext.isEnabled = currentIndex < problemList.size - 1
        binding.btnPrev.alpha = if (currentIndex > 0) 1.0f else 0.4f
        binding.btnNext.alpha = if (currentIndex < problemList.size - 1) 1.0f else 0.4f
        updateUndoButton()
        updateTrialButton()
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
            // 记录悔棋历史
            moveHistory.add(Pair(currentBoardString, binding.boardView.lastMoveIndex))
            val prev = currentBoardString; placeStone(index, problem, expected.color)
            if (currentBoardString != prev) {
                currentSolutionIndex++; playStoneSound()
                updateUndoButton()
                if (currentSolutionIndex >= moves.size) {
                    // 全部答完才显示正解+庆祝动画
                    isSolved = true; showSuccess()
                } else {
                    val next = moves[currentSolutionIndex]
                    if (next.color != problem.toPlay) {
                        // 对手应手，不显示"正确"
                        binding.boardView.postDelayed({ autoPlayOpponent() }, 500)
                    }
                    // 自己的下一步，也不显示"正确"，安静等待落子
                }
            } else { moveHistory.removeAt(moveHistory.size - 1); enterTrialMode() }
        } else { 
            // 点错了，提示错误
            showFeedbackOverlay("错误", false)
        }
    }
    
    private fun enterTrialMode() {
        val problem = problemList[currentIndex]
        isTrialMode = true; trialBoardString = currentBoardString; trialStoneIndices.clear(); trialCurrentPlayer = if (currentSolutionIndex > 0) binding.boardView.currentPlayer else problem.toPlay
        binding.boardView.trialModeEnabled = true; binding.boardView.trialStoneIndices = emptySet()
        binding.tvTrialMode.visibility = View.VISIBLE; binding.btnExitTrial.visibility = View.VISIBLE
        binding.btnTrial.text = "退出试下"
        binding.btnTrial.setTextColor(ContextCompat.getColor(this, R.color.accent))
        showFeedbackOverlay("试下模式", false)
    }
    
    private fun exitTrialMode() {
        isTrialMode = false; trialBoardString = ""; trialStoneIndices.clear()
        binding.boardView.trialModeEnabled = false; binding.boardView.trialStoneIndices = emptySet()
        binding.tvTrialMode.visibility = View.GONE; binding.btnExitTrial.visibility = View.GONE
        binding.btnTrial.text = "试下"
        binding.btnTrial.setTextColor(ContextCompat.getColor(this, R.color.white))
    }
    
    private fun handleTrialClick(index: Int) {
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
        // 记录悔棋历史
        moveHistory.add(Pair(currentBoardString, binding.boardView.lastMoveIndex))
        placeStone(index, problem, move.color)
        if (currentBoardString != prev) {
            currentSolutionIndex++; playStoneSound()
            updateUndoButton()
            if (currentSolutionIndex >= moves.size) { isSolved = true; isAutoPlaying = false; showSuccess(); return }
            val next = moves[currentSolutionIndex]
            if (next.color != problem.toPlay) binding.boardView.postDelayed({ playOpponentMove() }, 300)
            else { isAutoPlaying = false /* 不显示"正确"，安静等待 */ }
        } else { isAutoPlaying = false; moveHistory.removeAt(moveHistory.size - 1); enterTrialMode() }
    }
    
    private fun placeStone(index: Int, problem: Problem, color: StoneColor) {
        currentBoardString = GoBoard.placeStone(currentBoardString, index, color, 13)
        binding.boardView.lastMoveIndex = index; binding.boardView.currentPlayer = if (color == StoneColor.BLACK) StoneColor.WHITE else StoneColor.BLACK
        binding.boardView.updateBoard(currentBoardString, index)
    }
    
    private fun handleUndo() {
        if (isShowingAnswer || isAutoPlaying) return
        if (isTrialMode) {
            // 试下模式悔棋：恢复到试下前的状态
            exitTrialMode()
            showCurrentProblem()
            return
        }
        if (moveHistory.isEmpty()) return
        val (prevBoard, prevLastMove) = moveHistory.removeAt(moveHistory.size - 1)
        currentSolutionIndex--
        currentBoardString = prevBoard
        binding.boardView.lastMoveIndex = prevLastMove
        // 恢复当前玩家
        val problem = problemList[currentIndex]
        if (currentSolutionIndex > 0) {
            val lastMove = problem.solutionMoves[currentSolutionIndex - 1]
            binding.boardView.currentPlayer = if (lastMove.color == StoneColor.BLACK) StoneColor.WHITE else StoneColor.BLACK
        } else {
            binding.boardView.currentPlayer = problem.toPlay
        }
        binding.boardView.updateBoard(currentBoardString, prevLastMove)
        isSolved = false
        updateUndoButton()
    }
    
    private fun updateUndoButton() {
        val canUndo = !isShowingAnswer && !isAutoPlaying && (moveHistory.isNotEmpty() || isTrialMode)
        binding.btnUndo.isEnabled = canUndo
        binding.btnUndo.alpha = if (canUndo) 1.0f else 0.4f
    }
    
    private fun updateTrialButton() {
        if (isTrialMode) {
            binding.btnTrial.text = "退出试下"
            binding.btnTrial.setTextColor(ContextCompat.getColor(this, R.color.accent))
        } else {
            binding.btnTrial.text = "试下"
            binding.btnTrial.setTextColor(ContextCompat.getColor(this, R.color.white))
        }
    }
    
    private fun showSuccess() {
        showFeedbackOverlay("✨ 正解！", true)
        showCelebration()
        binding.btnShowAnswer.text = "下一题"; binding.btnShowAnswer.setOnClickListener { if (currentIndex < problemList.size - 1) { currentIndex++; showCurrentProblem() }; binding.btnShowAnswer.setOnClickListener { showFullAnswer() } }
    }
    
    /**
     * 庆祝动画：粒子星星散落
     */
    private fun showCelebration() {
        val container = binding.boardContainer
        val celebration = CelebrationView(this)
        container.addView(celebration, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        // 3秒后自动移除
        celebration.postDelayed({ container.removeView(celebration) }, 3000)
    }
    
    /**
     * 粒子庆祝动画View - 星星散落效果
     */
    private class CelebrationView(context: Context) : View(context) {
        private data class Particle(
            var x: Float, var y: Float,
            var vx: Float, var vy: Float,
            var size: Float, var alpha: Float,
            var color: Int, var rotation: Float,
            var rotationSpeed: Float
        )
        
        private val particles = mutableListOf<Particle>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var startTime = System.currentTimeMillis()
        private val duration = 2500L // ms
        
        private val starColors = intArrayOf(
            0xFFC9A96E.toInt(), // 金色
            0xFF66BB6A.toInt(), // 绿色
            0xFFE91E63.toInt(), // 粉红
            0xFFFFD54F.toInt(), // 黄色
            0xFF42A5F5.toInt(), // 蓝色
            0xFFFFFFFF.toInt()  // 白色
        )
        
        init {
            // 从中心点向外散射粒子
            val random = Random.Default
            for (i in 0..49) {
                val angle = random.nextDouble() * Math.PI * 2
                val speed = (random.nextDouble() * 6 + 2).toFloat()
                particles.add(Particle(
                    x = 0.5f, y = 0.5f, // normalized center
                    vx = (cos(angle) * speed).toFloat(),
                    vy = (sin(angle) * speed - 3).toFloat(), // slightly upward bias
                    size = random.nextDouble().toFloat() * 6 + 4,
                    alpha = 1.0f,
                    color = starColors[random.nextInt(starColors.size)],
                    rotation = random.nextDouble().toFloat() * 360,
                    rotationSpeed = (random.nextDouble() * 6 - 3).toFloat()
                ))
            }
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > duration) return
            
            val progress = elapsed.toFloat() / duration
            val w = width.toFloat()
            val h = height.toFloat()
            val gravity = 0.15f
            
            for (p in particles) {
                // Update physics
                p.x += p.vx / w
                p.vy += gravity
                p.y += p.vy / h
                p.rotation += p.rotationSpeed
                p.alpha = 1.0f - progress * progress // ease out fade
                
                if (p.alpha <= 0) continue
                
                val px = p.x * w
                val py = p.y * h
                
                canvas.save()
                canvas.translate(px, py)
                canvas.rotate(p.rotation)
                
                paint.color = p.color
                paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
                
                drawStar(canvas, paint, p.size)
                
                canvas.restore()
            }
            
            if (elapsed < duration) {
                invalidate()
            }
        }
        
        private fun drawStar(canvas: Canvas, paint: Paint, size: Float) {
            // 四角星
            val path = android.graphics.Path()
            val outerR = size
            val innerR = size * 0.35f
            for (i in 0..3) {
                val outerAngle = Math.toRadians(i * 90.0 - 45.0)
                val innerAngle = Math.toRadians(i * 90.0)
                if (i == 0) {
                    path.moveTo((cos(outerAngle) * outerR).toFloat(), (sin(outerAngle) * outerR).toFloat())
                } else {
                    path.lineTo((cos(outerAngle) * outerR).toFloat(), (sin(outerAngle) * outerR).toFloat())
                }
                path.lineTo((cos(innerAngle) * innerR).toFloat(), (sin(innerAngle) * innerR).toFloat())
            }
            path.close()
            canvas.drawPath(path, paint)
        }
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
        
        if (isCorrect && msg.contains("正解")) {
            // 正解：更大的弹入动画 + 金色发光
            overlay.textSize = 34f
            val scaleX = ObjectAnimator.ofFloat(overlay, "scaleX", 0.3f, 1.2f, 1.0f)
            val scaleY = ObjectAnimator.ofFloat(overlay, "scaleY", 0.3f, 1.2f, 1.0f)
            scaleX.duration = 400; scaleY.duration = 400
            val set = AnimatorSet()
            set.playTogether(scaleX, scaleY)
            set.start()
            
            // 延迟淡出
            overlay.postDelayed({
                val fadeOut = ObjectAnimator.ofFloat(overlay, "alpha", 1.0f, 0f)
                fadeOut.duration = 800
                fadeOut.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        overlay.visibility = View.GONE
                        overlay.textSize = 28f // reset
                    }
                })
                fadeOut.start()
            }, 2200)
        } else {
            // 错误/试下提示：原来的动画
            overlay.textSize = 28f
            val scaleX = ObjectAnimator.ofFloat(overlay, "scaleX", 0.5f, 1.1f, 1.0f)
            val scaleY = ObjectAnimator.ofFloat(overlay, "scaleY", 0.5f, 1.1f, 1.0f)
            scaleX.duration = 300; scaleY.duration = 300
            scaleX.start(); scaleY.start()
            
            // 延迟淡出
            overlay.postDelayed({
                val fadeOut = ObjectAnimator.ofFloat(overlay, "alpha", 1.0f, 0f)
                fadeOut.duration = 600
                fadeOut.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        overlay.visibility = View.GONE
                    }
                })
                fadeOut.start()
            }, 1000)
        }
    }
    
    private fun showFeedback(msg: String, ok: Boolean) {
        showFeedbackOverlay(msg, ok)
    }
}
