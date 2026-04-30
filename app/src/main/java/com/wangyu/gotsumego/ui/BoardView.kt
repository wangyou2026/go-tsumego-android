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

class BoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    var boardSize: Int = 19
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }
    
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
    
    // 局部放大参数
    var zoomEnabled: Boolean = false
    var zoomMinCol: Int = 0
    var zoomMaxCol: Int = 18
    var zoomMinRow: Int = 0
    var zoomMaxRow: Int = 18
    
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
    
    private var padding = 0f  // 动态计算，确保边线棋子完整显示
    
    private var cellSize = 0f
    private var stoneRadius = 0f
    private var starPointRadius = 0f
    
    // 缩放和平移参数
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val size = min(width, height)
        setMeasuredDimension(size, size)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 动态计算padding：至少半个棋子直径，确保边线棋子完整显示
        // 同时增加一些额外空间让棋盘更大
        val viewSize = min(w, h)
        padding = viewSize * 0.05f  // 5%的边距
        calculateDimensions()
    }
    
    private fun calculateDimensions() {
        val viewSize = min(width, height)
        val availableSize = viewSize - 2 * padding
        
        if (zoomEnabled) {
            // 局部放大模式：计算缩放比例
            val zoomCols = zoomMaxCol - zoomMinCol + 1
            val zoomRows = zoomMaxRow - zoomMinRow + 1
            val zoomSize = maxOf(zoomCols, zoomRows)
            
            if (zoomSize > 1) {
                cellSize = availableSize / (zoomSize - 1)
                scale = (boardSize - 1).toFloat() / (zoomSize - 1)
            } else {
                cellSize = availableSize
                scale = 1f
            }
            
            // 计算偏移，使局部区域居中
            val actualSize = (zoomSize - 1) * cellSize
            val extraSpace = availableSize - actualSize
            offsetX = padding - zoomMinCol * cellSize + extraSpace / 2
            offsetY = padding - zoomMinRow * cellSize + extraSpace / 2
        } else {
            // 标准模式
            if (boardSize > 1) {
                cellSize = availableSize / (boardSize - 1)
            } else {
                cellSize = availableSize
            }
            scale = 1f
            offsetX = padding
            offsetY = padding
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
        
        // 只绘制可见区域的网格线
        val visibleMinCol = if (zoomEnabled) zoomMinCol else 0
        val visibleMaxCol = if (zoomEnabled) zoomMaxCol else boardSize - 1
        val visibleMinRow = if (zoomEnabled) zoomMinRow else 0
        val visibleMaxRow = if (zoomEnabled) zoomMaxRow else boardSize - 1
        
        for (i in visibleMinRow..visibleMaxRow) {
            val y = offsetY + i * cellSize
            canvas.drawLine(offsetX + visibleMinCol * cellSize, y, offsetX + visibleMaxCol * cellSize, y, linePaint)
        }
        
        for (i in visibleMinCol..visibleMaxCol) {
            val x = offsetX + i * cellSize
            canvas.drawLine(x, offsetY + visibleMinRow * cellSize, x, offsetY + visibleMaxRow * cellSize, linePaint)
        }
    }
    
    private fun drawStarPoints(canvas: Canvas) {
        val starPoints = GoBoard.getStarPoints(boardSize)
        for (point in starPoints) {
            // 只绘制可见区域的星位
            if (zoomEnabled) {
                if (point.col < zoomMinCol || point.col > zoomMaxCol ||
                    point.row < zoomMinRow || point.row > zoomMaxRow) {
                    continue
                }
            }
            val x = offsetX + point.col * cellSize
            val y = offsetY + point.row * cellSize
            canvas.drawCircle(x, y, starPointRadius, starPointPaint)
        }
    }
    
    private fun drawStones(canvas: Canvas) {
        if (boardString.length != boardSize * boardSize) return
        
        for (i in boardString.indices) {
            val row = i / boardSize
            val col = i % boardSize
            
            // 局部放大模式下只绘制可见区域的棋子
            if (zoomEnabled) {
                if (col < zoomMinCol || col > zoomMaxCol ||
                    row < zoomMinRow || row > zoomMaxRow) {
                    continue
                }
            }
            
            val stone = boardString[i]
            
            if (stone == 'X') {
                drawStone(canvas, col, row, StoneColor.BLACK, i in trialStoneIndices)
            } else if (stone == 'O') {
                drawStone(canvas, col, row, StoneColor.WHITE, i in trialStoneIndices)
            }
        }
    }
    
    private fun drawStone(canvas: Canvas, col: Int, row: Int, stoneColor: StoneColor, isTrialStone: Boolean = false) {
        val centerX = offsetX + col * cellSize
        val centerY = offsetY + row * cellSize
        
        // 试下棋子半透明和特殊样式
        val alphaMultiplier = if (isTrialStone) 0.6f else 1.0f
        val trialMarkerRadius = if (isTrialStone) stoneRadius * 0.15f else 0f
        
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
            
            // 试下棋子标记：金色小三角形
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
            
            // 试下棋子标记：金色小三角形
            if (isTrialStone) {
                drawTrialMarker(canvas, centerX, centerY)
            }
        }
    }
    
    private fun drawTrialMarker(canvas: Canvas, centerX: Float, centerY: Float) {
        // 在棋子顶部绘制金色小三角形标记
        val markerSize = stoneRadius * 0.25f
        val markerY = centerY - stoneRadius - markerSize
        
        val path = Path()
        path.moveTo(centerX, markerY - markerSize)  // 顶点
        path.lineTo(centerX - markerSize * 0.866f, markerY + markerSize * 0.5f)  // 左下
        path.lineTo(centerX + markerSize * 0.866f, markerY + markerSize * 0.5f)  // 右下
        path.close()
        
        val markerPaint = Paint().apply {
            color = Color.parseColor("#C9A96E")  // 暖金色
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawPath(path, markerPaint)
    }
    
    private fun drawLastMoveMarker(canvas: Canvas, index: Int) {
        val col = index % boardSize
        val row = index / boardSize
        val centerX = offsetX + col * cellSize
        val centerY = offsetY + row * cellSize
        
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
        val centerX = offsetX + col * cellSize
        val centerY = offsetY + row * cellSize
        
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
            
            // 将触摸坐标转换为棋盘坐标
            val col = ((touchX - offsetX) / cellSize + 0.5f).toInt()
            val row = ((touchY - offsetY) / cellSize + 0.5f).toInt()
            
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
    
    /**
     * 设置局部放大区域
     * @param minCol 最小列号
     * @param maxCol 最大列号
     * @param minRow 最小行号
     * @param maxRow 最大行号
     */
    fun setZoomArea(minCol: Int, maxCol: Int, minRow: Int, maxRow: Int) {
        if (minCol == 0 && maxCol == boardSize - 1 && minRow == 0 && maxRow == boardSize - 1) {
            // 全盘显示
            zoomEnabled = false
        } else {
            zoomEnabled = true
            zoomMinCol = maxOf(0, minCol)
            zoomMaxCol = minOf(boardSize - 1, maxCol)
            zoomMinRow = maxOf(0, minRow)
            zoomMaxRow = minOf(boardSize - 1, maxRow)
        }
        calculateDimensions()
        invalidate()
    }
}
