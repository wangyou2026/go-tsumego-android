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

/**
 * 围棋棋盘自定义View - 美化版
 * 支持绘制9路、13路、19路棋盘
 * 支持触摸落子
 * 渐变色棋子和阴影效果
 */
class BoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // 棋盘大小 (9, 13, 19)
    var boardSize: Int = 9
        set(value) {
            field = value
            invalidate()
        }
    
    // 棋盘字符串，用于绘制棋子
    var boardString: String = ""
        set(value) {
            field = value
            invalidate()
        }
    
    // 当前下棋方
    var currentPlayer: StoneColor = StoneColor.BLACK
        set(value) {
            field = value
            invalidate()
        }
    
    // 最后一手的位置
    var lastMoveIndex: Int = -1
    
    // 正解位置（用于提示）
    var correctMoveIndex: Int = -1
    var showCorrectMove: Boolean = false
    
    // 提示位置
    var hintIndex: Int = -1
    var showHint: Boolean = false
    
    // 落子监听器
    var onStoneClickListener: ((Int) -> Unit)? = null
    
    // 颜色定义
    private val lineColor = context.getColor(R.color.board_line)
    private val hintColor = context.getColor(R.color.hint_point)
    private val lastMoveColor = context.getColor(R.color.last_move)
    
    // 画笔
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
    
    private val lastMovePaint = Paint().apply {
        color = lastMoveColor
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    // 棋盘边距
    private val padding = 40f
    
    // 计算属性
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
        calculateDimensions()
    }
    
    private fun calculateDimensions() {
        val size = min(width, height)
        val availableSize = size - 2 * padding
        cellSize = availableSize / (boardSize - 1)
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
        
        if (lastMoveIndex >= 0 && lastMoveIndex < boardString.length) {
            if (boardString[lastMoveIndex] != '.') {
                drawLastMoveMarker(canvas, lastMoveIndex)
            }
        }
        
        if (showCorrectMove && correctMoveIndex >= 0) {
            drawHintMarker(canvas, correctMoveIndex)
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
            color = Color.parseColor("#5D4037")
            style = Paint.Style.STROKE
            strokeWidth = 12f
            isAntiAlias = true
        }
        canvas.drawRect(rect, framePaint)
        
        val innerFramePaint = Paint().apply {
            color = Color.parseColor("#8B4513")
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
        val starPoints = GoBoard.getStarPoints(boardSize)
        
        val outerPaint = Paint().apply {
            color = Color.parseColor("#4A3520")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        for (point in starPoints) {
            val x = padding + point.col * cellSize
            val y = padding + point.row * cellSize
            canvas.drawCircle(x, y, starPointRadius * 1.3f, outerPaint)
        }
        
        for (point in starPoints) {
            val x = padding + point.col * cellSize
            val y = padding + point.row * cellSize
            canvas.drawCircle(x, y, starPointRadius, starPointPaint)
        }
    }
    
    private fun drawStones(canvas: Canvas) {
        if (boardString.length != boardSize * boardSize) return
        
        for (i in boardString.indices) {
            val row = i / boardSize
            val col = i % boardSize
            val stone = boardString[i]
            
            when (stone) {
                'X' -> drawStone(canvas, col, row, StoneColor.BLACK)
                'O' -> drawStone(canvas, col, row, StoneColor.WHITE)
            }
        }
    }
    
    private fun drawStone(canvas: Canvas, col: Int, row: Int, color: StoneColor) {
        val centerX = padding + col * cellSize
        val centerY = padding + row * cellSize
        
        when (color) {
            StoneColor.BLACK -> {
                canvas.drawCircle(centerX + 2f, centerY + 3f, stoneRadius, shadowPaint)
                
                val highlightX = centerX - stoneRadius * 0.3f
                val highlightY = centerY - stoneRadius * 0.3f
                
                val blackGradient = RadialGradient(
                    highlightX, highlightY, stoneRadius * 1.5f,
                    intArrayOf(
                        Color.parseColor("#5A5A5A"),
                        Color.parseColor("#2A2A2A"),
                        Color.parseColor("#000000")
                    ),
                    floatArrayOf(0f, 0.4f, 1f),
                    Shader.TileMode.CLAMP
                )
                
                blackStonePaint.shader = blackGradient
                canvas.drawCircle(centerX, centerY, stoneRadius, blackStonePaint)
                
                val hlPaint = Paint().apply {
                    this.color = Color.argb(80, 255, 255, 255)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawCircle(
                    centerX - stoneRadius * 0.35f,
                    centerY - stoneRadius * 0.35f,
                    stoneRadius * 0.2f,
                    hlPaint
                )
            }
            StoneColor.WHITE -> {
                canvas.drawCircle(centerX + 2f, centerY + 3f, stoneRadius, shadowPaint)
                
                val highlightX = centerX - stoneRadius * 0.3f
                val highlightY = centerY - stoneRadius * 0.3f
                
                val wGradient = RadialGradient(
                    highlightX, highlightY, stoneRadius * 1.5f,
                    intArrayOf(
                        Color.WHITE,
                        Color.parseColor("#F8F8F8"),
                        Color.parseColor("#E0E0E0")
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
                
                whiteStonePaint.shader = wGradient
                canvas.drawCircle(centerX, centerY, stoneRadius, whiteStonePaint)
                
                strokePaint.color = Color.parseColor("#CCCCCC")
                strokePaint.strokeWidth = 1.5f
                canvas.drawCircle(centerX, centerY, stoneRadius - 0.75f, strokePaint)
                
                val hlPaint = Paint().apply {
                    this.color = Color.argb(100, 255, 255, 255)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                canvas.drawCircle(
                    centerX - stoneRadius * 0.35f,
                    centerY - stoneRadius * 0.35f,
                    stoneRadius * 0.25f,
                    hlPaint
                )
            }
            StoneColor.EMPTY -> { }
        }
    }
    
    private fun drawLastMoveMarker(canvas: Canvas, index: Int) {
        val col = index % boardSize
        val row = index / boardSize
        val centerX = padding + col * cellSize
        val centerY = padding + row * cellSize
        
        val stoneColor = getStoneAt(index)
        val markerCol = if (stoneColor == StoneColor.BLACK) {
            Color.parseColor("#FF5252")
        } else {
            Color.parseColor("#FF1744")
        }
        
        val markerPaint = Paint().apply {
            color = markerCol
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
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
        
        val innerPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(centerX, centerY, stoneRadius * 0.15f, innerPaint)
        
        val outerPaint = Paint().apply {
            color = Color.parseColor("#FF9800")
            style = Paint.Style.STROKE
            strokeWidth = 3f
            alpha = 150
            isAntiAlias = true
        }
        canvas.drawCircle(centerX, centerY, stoneRadius * 0.7f, outerPaint)
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
        return when (boardString[index]) {
            'X' -> StoneColor.BLACK
            'O' -> StoneColor.WHITE
            else -> StoneColor.EMPTY
        }
    }
    
    fun getStoneAt(col: Int, row: Int): StoneColor {
        if (col < 0 || col >= boardSize || row < 0 || row >= boardSize) return StoneColor.EMPTY
        return getStoneAt(row * boardSize + col)
    }
    
    fun updateBoard(boardStr: String, lastMove: Int = -1) {
        this.boardString = boardStr
        this.lastMoveIndex = lastMove
        invalidate()
    }
    
    fun setCorrectMove(index: Int, show: Boolean) {
        this.correctMoveIndex = index
        this.showCorrectMove = show
        invalidate()
    }
}
