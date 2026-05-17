package com.wangyu.gotsumego.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.wangyu.gotsumego.R
import com.wangyu.gotsumego.data.StoneColor
import com.wangyu.gotsumego.util.GoBoard
import kotlin.math.min

class GoBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // 固定13路棋盘
    var boardSize: Int = 13
        private set
    
    var boardString: String = ""
        set(value) {
            field = value
            invalidate()
        }
    
    var currentPlayer: StoneColor = StoneColor.BLACK
    
    var lastMoveIndex: Int = -1
    
    var hintIndex: Int = -1
    var showHint: Boolean = false
    
    var onStoneClickListener: ((Int) -> Unit)? = null
    
    // 答案手数编号：key=棋盘index, value=手数编号
    var answerMoveIndices: Map<Int, Int> = emptyMap()
        set(value) {
            field = value
            invalidate()
        }
    
    // 试下模式参数
    var trialModeEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
    
    var trialStoneIndices: Set<Int> = emptySet()
        set(value) {
            field = value
            invalidate()
        }
    
    private val lineColor = context.getColor(R.color.board_line)
    private val hintColor = context.getColor(R.color.hint_point)
    
    private val blackStonePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val whiteStonePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    private val linePaint = Paint().apply {
        color = lineColor
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    
    private val starPointPaint = Paint().apply {
        color = lineColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val shadowPaint = Paint().apply {
        color = Color.argb(60, 0, 0, 0)
        isAntiAlias = true
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }
    
    private val hintPaint = Paint().apply {
        color = hintColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private var padding = 0f
    private var cellSize = 0f
    private var stoneRadius = 0f
    private var starPointRadius = 0f
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val viewSize = min(w, h)
        padding = viewSize * 0.05f
        calculateDimensions()
    }
    
    private fun calculateDimensions() {
        val viewSize = min(width, height)
        val availableSize = viewSize - 2 * padding
        
        // 固定13路棋盘
        if (boardSize > 1) {
            cellSize = availableSize / (boardSize - 1)
        } else {
            cellSize = availableSize
        }
        
        stoneRadius = cellSize * 0.45f
        starPointRadius = cellSize * 0.12f
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        calculateDimensions()
        drawBoard(canvas)
        drawGridLines(canvas)
        drawStarPoints(canvas)
        drawStones(canvas)
        
        // 绘制答案手数编号
        if (answerMoveIndices.isNotEmpty()) {
            drawAnswerMoveNumbers(canvas)
        }
        
        if (lastMoveIndex >= 0 && lastMoveIndex < boardString.length) {
            if (boardString[lastMoveIndex] != '.') {
                drawLastMoveMarker(canvas, lastMoveIndex)
            }
        }
        
        if (showHint && hintIndex >= 0 && hintIndex < boardString.length) {
            drawHintMarker(canvas, hintIndex)
        }
    }
    
    private fun drawBoard(canvas: Canvas) {
        val woodColors = intArrayOf(
            Color.parseColor("#E8D4A8"),
            Color.parseColor("#DEB887"),
            Color.parseColor("#D4A76A")
        )
        val woodGradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            woodColors, null, Shader.TileMode.CLAMP
        )
        
        val boardPaint = Paint().apply {
            shader = woodGradient
            style = Paint.Style.FILL
        }
        
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRect(rect, boardPaint)
        
        val framePaint = Paint().apply {
            this.color = Color.parseColor("#5D4037")
            style = Paint.Style.STROKE
            strokeWidth = 12f
            isAntiAlias = true
        }
        canvas.drawRect(rect, framePaint)
        
        val innerFramePaint = Paint().apply {
            this.color = Color.parseColor("#8B4513")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        val innerRect = RectF(6f, 6f, width - 6f, height - 6f)
        canvas.drawRect(innerRect, innerFramePaint)
    }
    
    private fun drawGridLines(canvas: Canvas) {
        linePaint.color = Color.parseColor("#6B4423")
        linePaint.strokeWidth = 2f
        
        // 13路棋盘全盘显示
        for (i in 0 until boardSize) {
            val y = padding + i * cellSize
            canvas.drawLine(padding, y, padding + (boardSize - 1) * cellSize, y, linePaint)
        }
        
        for (i in 0 until boardSize) {
            val x = padding + i * cellSize
            canvas.drawLine(x, padding, x, padding + (boardSize - 1) * cellSize, linePaint)
        }
    }
    
    private fun drawStarPoints(canvas: Canvas) {
        // 13路棋盘的星位
        val starPoints13 = listOf(
            Pair(3, 3), Pair(3, 9), Pair(9, 3), Pair(9, 9), Pair(6, 6)
        )
        for ((col, row) in starPoints13) {
            val x = padding + col * cellSize
            val y = padding + row * cellSize
            canvas.drawCircle(x, y, starPointRadius, starPointPaint)
        }
    }
    
    private fun drawStones(canvas: Canvas) {
        if (boardString.length != boardSize * boardSize) return
        
        for (i in boardString.indices) {
            val row = i / boardSize
            val col = i % boardSize
            
            val stone = boardString[i]
            
            if (stone == 'X') {
                drawStone(canvas, col, row, StoneColor.BLACK, i in trialStoneIndices)
            } else if (stone == 'O') {
                drawStone(canvas, col, row, StoneColor.WHITE, i in trialStoneIndices)
            }
        }
    }
    
    private fun drawStone(canvas: Canvas, col: Int, row: Int, stoneColor: StoneColor, isTrialStone: Boolean = false) {
        val centerX = padding + col * cellSize
        val centerY = padding + row * cellSize
        
        val alphaMultiplier = if (isTrialStone) 0.6f else 1.0f
        
        if (stoneColor == StoneColor.BLACK) {
            canvas.drawCircle(centerX + 2f, centerY + 3f, stoneRadius, shadowPaint)
            
            val blackGradient = RadialGradient(
                centerX - stoneRadius * 0.3f, centerY - stoneRadius * 0.3f, stoneRadius * 1.5f,
                intArrayOf(
                    Color.parseColor("#5A5A5A"),
                    Color.parseColor("#2A2A2A"),
                    Color.parseColor("#000000")
                ),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
            
            blackStonePaint.shader = blackGradient
            blackStonePaint.alpha = (255 * alphaMultiplier).toInt()
            canvas.drawCircle(centerX, centerY, stoneRadius, blackStonePaint)
            
            val hlPaint = Paint()
            hlPaint.color = Color.argb((80 * alphaMultiplier).toInt(), 255, 255, 255)
            hlPaint.style = Paint.Style.FILL
            hlPaint.isAntiAlias = true
            canvas.drawCircle(
                centerX - stoneRadius * 0.35f,
                centerY - stoneRadius * 0.35f,
                stoneRadius * 0.2f,
                hlPaint
            )
            
            if (isTrialStone) {
                drawTrialMarker(canvas, centerX, centerY)
            }
        } else if (stoneColor == StoneColor.WHITE) {
            canvas.drawCircle(centerX + 2f, centerY + 3f, stoneRadius, shadowPaint)
            
            val wGradient = RadialGradient(
                centerX - stoneRadius * 0.3f, centerY - stoneRadius * 0.3f, stoneRadius * 1.5f,
                intArrayOf(
                    Color.WHITE,
                    Color.parseColor("#F8F8F8"),
                    Color.parseColor("#E0E0E0")
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            
            whiteStonePaint.shader = wGradient
            whiteStonePaint.alpha = (255 * alphaMultiplier).toInt()
            canvas.drawCircle(centerX, centerY, stoneRadius, whiteStonePaint)
            
            strokePaint.color = Color.parseColor("#CCCCCC")
            strokePaint.strokeWidth = 1.5f
            canvas.drawCircle(centerX, centerY, stoneRadius - 0.75f, strokePaint)
            
            val hlPaint = Paint()
            hlPaint.color = Color.argb((100 * alphaMultiplier).toInt(), 255, 255, 255)
            hlPaint.style = Paint.Style.FILL
            hlPaint.isAntiAlias = true
            canvas.drawCircle(
                centerX - stoneRadius * 0.35f,
                centerY - stoneRadius * 0.35f,
                stoneRadius * 0.25f,
                hlPaint
            )
            
            if (isTrialStone) {
                drawTrialMarker(canvas, centerX, centerY)
            }
        }
    }
    
    private fun drawTrialMarker(canvas: Canvas, centerX: Float, centerY: Float) {
        val markerSize = stoneRadius * 0.25f
        val markerY = centerY - stoneRadius - markerSize
        
        val path = Path()
        path.moveTo(centerX, markerY - markerSize)
        path.lineTo(centerX - markerSize * 0.866f, markerY + markerSize * 0.5f)
        path.lineTo(centerX + markerSize * 0.866f, markerY + markerSize * 0.5f)
        path.close()
        
        val markerPaint = Paint().apply {
            color = Color.parseColor("#C9A96E")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawPath(path, markerPaint)
    }
    
    private fun drawAnswerMoveNumbers(canvas: Canvas) {
        if (boardString.length != boardSize * boardSize) return
        
        val textSize = stoneRadius * 0.8f
        val numberPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
            this.textSize = textSize
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        
        for ((index, moveNumber) in answerMoveIndices) {
            if (index < 0 || index >= boardString.length) continue
            val stone = boardString[index]
            if (stone != 'X' && stone != 'O') continue
            
            val col = index % boardSize
            val row = index / boardSize
            val centerX = padding + col * cellSize
            val centerY = padding + row * cellSize
            
            // 黑棋上画白色数字，白棋上画黑色数字
            numberPaint.color = if (stone == 'X') Color.WHITE else Color.parseColor("#222222")
            
            val textBounds = Rect()
            val text = moveNumber.toString()
            numberPaint.getTextBounds(text, 0, text.length, textBounds)
            val textY = centerY + textBounds.height() / 2f
            
            canvas.drawText(text, centerX, textY, numberPaint)
        }
    }
    
    private fun drawLastMoveMarker(canvas: Canvas, index: Int) {
        val col = index % boardSize
        val row = index / boardSize
        val centerX = padding + col * cellSize
        val centerY = padding + row * cellSize
        
        val stoneColor = getStoneAt(index)
        val markerColor = if (stoneColor == StoneColor.BLACK) {
            Color.parseColor("#FF5252")
        } else {
            Color.parseColor("#FF1744")
        }
        
        val markerPaint = Paint()
        markerPaint.color = markerColor
        markerPaint.style = Paint.Style.FILL
        markerPaint.isAntiAlias = true
        
        canvas.drawCircle(centerX, centerY, stoneRadius * 0.2f, markerPaint)
    }
    
    private fun drawHintMarker(canvas: Canvas, index: Int) {
        val col = index % boardSize
        val row = index / boardSize
        val centerX = padding + col * cellSize
        val centerY = padding + row * cellSize
        
        val hintGradient = RadialGradient(
            centerX, centerY, stoneRadius * 0.6f,
            intArrayOf(
                Color.parseColor("#FF9800"),
                Color.parseColor("#FF5722")
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        
        hintPaint.shader = hintGradient
        hintPaint.alpha = 220
        canvas.drawCircle(centerX, centerY, stoneRadius * 0.5f, hintPaint)
        
        val innerPaint = Paint()
        innerPaint.color = Color.WHITE
        innerPaint.style = Paint.Style.FILL
        innerPaint.isAntiAlias = true
        canvas.drawCircle(centerX, centerY, stoneRadius * 0.15f, innerPaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val touchX = event.x
            val touchY = event.y
            
            val col = ((touchX - padding) / cellSize + 0.5f).toInt()
            val row = ((touchY - padding) / cellSize + 0.5f).toInt()
            
            if (col in 0 until boardSize && row in 0 until boardSize) {
                val index = row * boardSize + col
                onStoneClickListener?.invoke(index)
            }
            
            return true
        }
        
        return true
    }
    
    fun getStoneAt(index: Int): StoneColor {
        if (index < 0 || index >= boardString.length) return StoneColor.EMPTY
        val ch = boardString[index]
        return if (ch == 'X') {
            StoneColor.BLACK
        } else if (ch == 'O') {
            StoneColor.WHITE
        } else {
            StoneColor.EMPTY
        }
    }
    
    fun updateBoard(boardStr: String, lastMove: Int = -1) {
        this.boardString = boardStr
        this.lastMoveIndex = lastMove
        invalidate()
    }
}
