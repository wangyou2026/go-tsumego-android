package com.wangyu.gotsumego.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.wangyu.gotsumego.R
import com.wangyu.gotsumego.data.Position
import com.wangyu.gotsumego.data.StoneColor
import com.wangyu.gotsumego.util.GoBoard
import kotlin.math.min

/**
 * 围棋棋盘自定义View
 * 支持绘制9路、13路、19路棋盘
 * 支持触摸落子
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
    // 格式: 长度为 boardSize * boardSize
    // '.' = 空, 'X' = 黑, 'O' = 白
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
    
    // 落子监听器
    var onStoneClickListener: ((Int) -> Unit)? = null
    
    // 颜色定义
    private val boardColor = context.getColor(R.color.board_wood)
    private val lineColor = context.getColor(R.color.board_line)
    private val blackStoneColor = context.getColor(R.color.stone_black)
    private val whiteStoneColor = context.getColor(R.color.stone_white)
    private val blackStrokeColor = context.getColor(R.color.stone_black_stroke)
    private val whiteStrokeColor = context.getColor(R.color.stone_white_stroke)
    private val hintColor = context.getColor(R.color.hint_point)
    private val lastMoveColor = context.getColor(R.color.last_move)
    
    // 画笔
    private val boardPaint = Paint().apply {
        color = boardColor
        style = Paint.Style.FILL
    }
    
    private val linePaint = Paint().apply {
        color = lineColor
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    
    private val stonePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    private val starPointPaint = Paint().apply {
        color = lineColor
        style = Paint.Style.FILL
        isAntiAlias = true
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
        
        // 保持正方形
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
        
        // 计算每格大小
        cellSize = availableSize / (boardSize - 1)
        stoneRadius = cellSize * 0.45f
        starPointRadius = cellSize * 0.12f
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        calculateDimensions()
        
        // 绘制棋盘背景（木质）
        drawBoard(canvas)
        
        // 绘制网格线
        drawGridLines(canvas)
        
        // 绘制星位
        drawStarPoints(canvas)
        
        // 绘制棋子
        drawStones(canvas)
        
        // 绘制最后一手标记
        if (lastMoveIndex >= 0 && lastMoveIndex < boardString.length) {
            if (boardString[lastMoveIndex] != '.') {
                drawLastMoveMarker(canvas, lastMoveIndex)
            }
        }
        
        // 绘制正解提示
        if (showCorrectMove && correctMoveIndex >= 0) {
            drawHintMarker(canvas, correctMoveIndex)
        }
    }
    
    /**
     * 绘制棋盘背景
     */
    private fun drawBoard(canvas: Canvas) {
        val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRect(rect, boardPaint)
        
        // 绘制边框
        strokePaint.color = lineColor
        strokePaint.strokeWidth = 4f
        canvas.drawRect(rect, strokePaint)
        strokePaint.strokeWidth = 2f
    }
    
    /**
     * 绘制网格线
     */
    private fun drawGridLines(canvas: Canvas) {
        // 绘制横线
        for (i in 0 until boardSize) {
            val y = padding + i * cellSize
            canvas.drawLine(padding, y, padding + (boardSize - 1) * cellSize, y, linePaint)
        }
        
        // 绘制竖线
        for (i in 0 until boardSize) {
            val x = padding + i * cellSize
            canvas.drawLine(x, padding, x, padding + (boardSize - 1) * cellSize, linePaint)
        }
    }
    
    /**
     * 绘制星位
     */
    private fun drawStarPoints(canvas: Canvas) {
        val starPoints = GoBoard.getStarPoints(boardSize)
        for (point in starPoints) {
            val x = padding + point.col * cellSize
            val y = padding + point.row * cellSize
            canvas.drawCircle(x, y, starPointRadius, starPointPaint)
        }
    }
    
    /**
     * 绘制所有棋子
     */
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
    
    /**
     * 绘制单个棋子
     */
    private fun drawStone(canvas: Canvas, col: Int, row: Int, color: StoneColor) {
        val centerX = padding + col * cellSize
        val centerY = padding + row * cellSize
        
        when (color) {
            StoneColor.BLACK -> {
                stonePaint.color = blackStoneColor
                canvas.drawCircle(centerX, centerY, stoneRadius, stonePaint)
            }
            StoneColor.WHITE -> {
                stonePaint.color = whiteStoneColor
                strokePaint.color = whiteStrokeColor
                canvas.drawCircle(centerX, centerY, stoneRadius, stonePaint)
                canvas.drawCircle(centerX, centerY, stoneRadius, strokePaint)
            }
            StoneColor.EMPTY -> { /* 不绘制 */ }
        }
    }
    
    /**
     * 绘制最后一手标记
     */
    private fun drawLastMoveMarker(canvas: Canvas, index: Int) {
        val col = index % boardSize
        val row = index / boardSize
        val centerX = padding + col * cellSize
        val centerY = padding + row * cellSize
        
        // 绘制一个小圆点标记
        canvas.drawCircle(centerX, centerY, stoneRadius * 0.25f, lastMovePaint)
    }
    
    /**
     * 绘制正解提示标记
     */
    private fun drawHintMarker(canvas: Canvas, index: Int) {
        val col = index % boardSize
        val row = index / boardSize
        val centerX = padding + col * cellSize
        val centerY = padding + row * cellSize
        
        // 绘制提示圆圈
        hintPaint.alpha = 180
        canvas.drawCircle(centerX, centerY, stoneRadius * 0.4f, hintPaint)
        
        // 绘制内部小点
        val innerPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(centerX, centerY, stoneRadius * 0.15f, innerPaint)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val touchX = event.x
            val touchY = event.y
            
            // 计算最近的交叉点
            val col = ((touchX - padding) / cellSize + 0.5f).toInt()
            val row = ((touchY - padding) / cellSize + 0.5f).toInt()
            
            // 检查是否在棋盘范围内
            if (col in 0 until boardSize && row in 0 until boardSize) {
                // 计算index
                val index = row * boardSize + col
                
                // 触发监听器
                onStoneClickListener?.invoke(index)
            }
            
            return true
        }
        
        return true
    }
    
    /**
     * 获取棋盘上指定位置的棋子颜色
     */
    fun getStoneAt(index: Int): StoneColor {
        if (index < 0 || index >= boardString.length) return StoneColor.EMPTY
        return when (boardString[index]) {
            'X' -> StoneColor.BLACK
            'O' -> StoneColor.WHITE
            else -> StoneColor.EMPTY
        }
    }
    
    /**
     * 获取棋盘上指定位置的棋子颜色（通过行列）
     */
    fun getStoneAt(col: Int, row: Int): StoneColor {
        if (col < 0 || col >= boardSize || row < 0 || row >= boardSize) return StoneColor.EMPTY
        return getStoneAt(row * boardSize + col)
    }
    
    /**
     * 更新棋盘显示
     */
    fun updateBoard(boardStr: String, lastMove: Int = -1) {
        this.boardString = boardStr
        this.lastMoveIndex = lastMove
        invalidate()
    }
    
    /**
     * 显示/隐藏正解提示
     */
    fun setCorrectMove(index: Int, show: Boolean) {
        this.correctMoveIndex = index
        this.showCorrectMove = show
        invalidate()
    }
}
