package com.wangyu.gotsumego.data

enum class StoneColor(val value: Int, val symbol: Char) {
    EMPTY(0, '.'),
    BLACK(1, 'X'),
    WHITE(2, 'O');
    
    companion object {
        fun fromValue(value: Int): StoneColor {
            return entries.find { it.value == value } ?: EMPTY
        }
    }
}

enum class ProblemType(val key: String, val displayName: String, val emoji: String) {
    LIFE_DEATH("life_death", "死活题", "🎯"),
    TESUJI("tesuji", "手筋题", "⚡"),
    YOSE("yose", "官子题", "💎"),
    CAPTURE("capture", "吃子题", "♟️"),
    UNKNOWN("unknown", "其他", "❓");
    
    companion object {
        fun fromKey(key: String): ProblemType {
            return entries.find { it.key == key } ?: UNKNOWN
        }
    }
}

data class Problem(
    val id: Int,
    val type: ProblemType,
    val difficulty: Int,
    val title: String,
    val boardSize: Int,
    val stones: List<Stone>,
    val toPlay: StoneColor,
    val correctMoves: List<Position>,
    val solutionMoves: List<SolutionMove>,
    val hint: String?,
    val solutionComment: String?,
    val book: String,
    // 裁剪区域
    val cropLeft: Int,
    val cropTop: Int,
    val cropSize: Int
) {
    /**
     * 是否需要裁剪显示
     * 当棋子分布范围小于棋盘大小，且裁剪后有足够显示空间时启用
     */
    val shouldCrop: Boolean
        get() = cropSize > 0 && cropSize < boardSize
    
    /**
     * 生成完整棋盘字符串
     */
    fun toBoardString(): String {
        val sb = StringBuilder()
        repeat(boardSize * boardSize) { index ->
            val row = index / boardSize
            val col = index % boardSize
            val stone = stones.find { it.row == row && it.col == col }
            sb.append(stone?.color?.symbol ?: StoneColor.EMPTY.symbol)
        }
        return sb.toString()
    }
    
    /**
     * 生成裁剪后的棋盘字符串
     * 大小为 cropSize x cropSize
     */
    fun toCroppedBoardString(): String {
        if (!shouldCrop) return toBoardString()
        
        val sb = StringBuilder()
        for (row in 0 until cropSize) {
            for (col in 0 until cropSize) {
                val globalRow = cropTop + row
                val globalCol = cropLeft + col
                val stone = stones.find { it.row == globalRow && it.col == globalCol }
                sb.append(stone?.color?.symbol ?: StoneColor.EMPTY.symbol)
            }
        }
        return sb.toString()
    }
    
    /**
     * 全局坐标转裁剪坐标
     */
    fun globalToCropped(globalCol: Int, globalRow: Int): Pair<Int, Int> {
        return Pair(globalCol - cropLeft, globalRow - cropTop)
    }
    
    /**
     * 裁剪坐标转全局坐标
     */
    fun croppedToGlobal(croppedCol: Int, croppedRow: Int): Pair<Int, Int> {
        return Pair(croppedCol + cropLeft, croppedRow + cropTop)
    }
    
    val firstCorrectMove: Position?
        get() = correctMoves.firstOrNull()
}

data class Position(
    val col: Int,
    val row: Int
) {
    fun toIndex(boardSize: Int): Int = row * boardSize + col
    
    companion object {
        fun fromIndex(index: Int, boardSize: Int): Position {
            return Position(col = index % boardSize, row = index / boardSize)
        }
    }
}

data class Stone(
    val col: Int,
    val row: Int,
    val color: StoneColor
)

data class SolutionMove(
    val col: Int,
    val row: Int,
    val color: StoneColor
) {
    fun toPosition(): Position = Position(col, row)
    fun toIndex(boardSize: Int): Int = row * boardSize + col
}

fun JsonProblem.toProblem(): Problem {
    // JSON 里存的是 display 坐标（row 0 在顶部），不需要翻转
    val stoneList = stones.mapNotNull { stoneData ->
        if (stoneData.size >= 3) {
            Stone(
                col = stoneData[0],
                row = stoneData[1],  // 直接使用，不翻转
                color = StoneColor.fromValue(stoneData[2])
            )
        } else null
    }
    
    // 答案坐标也是 display 坐标
    val moves = if (answer.size >= 2) {
        listOf(Position(col = answer[0], row = answer[1]))  // 直接使用，不翻转
    } else emptyList()
    
    // solutionMoves: [col, row, color] 格式，直接使用不翻转
    val solutionMoveList = solutionMoves?.mapNotNull { move ->
        if (move.size >= 3) {
            SolutionMove(
                col = move[0],
                row = move[1],  // 直接使用，不翻转
                color = StoneColor.fromValue(move[2])
            )
        } else null
    } ?: emptyList()
    
    // 计算裁剪区域
    val (cropLeft, cropTop, cropSize) = calculateCropArea(stoneList, boardSize)
    
    return Problem(
        id = id,
        type = ProblemType.fromKey(type),
        difficulty = difficulty,
        title = title,
        boardSize = boardSize,
        stones = stoneList,
        toPlay = StoneColor.fromValue(toPlay),
        correctMoves = moves,
        solutionMoves = solutionMoveList,
        hint = hint,
        solutionComment = solutionComment,
        book = book ?: "其他",
        cropLeft = cropLeft,
        cropTop = cropTop,
        cropSize = cropSize
    )
}

/**
 * 计算裁剪区域
 * 返回 (cropLeft, cropTop, cropSize)
 */
private fun calculateCropArea(stones: List<Stone>, boardSize: Int): Triple<Int, Int, Int> {
    if (stones.isEmpty()) return Triple(0, 0, boardSize)
    
    // 找到棋子的边界
    val cols = stones.map { it.col }
    val rows = stones.map { it.row }
    
    var minCol = cols.min()
    var maxCol = cols.max()
    var minRow = rows.min()
    var maxRow = rows.max()
    
    // 添加边距（3格）
    val margin = 3
    minCol = maxOf(0, minCol - margin)
    maxCol = minOf(boardSize - 1, maxCol + margin)
    minRow = maxOf(0, minRow - margin)
    maxRow = minOf(boardSize - 1, maxRow + margin)
    
    // 计算宽高
    val width = maxCol - minCol + 1
    val height = maxRow - minRow + 1
    
    // 取较大值作为正方形大小
    val size = maxOf(width, height)
    
    // 确保不超出棋盘边界
    var cropLeft = minCol
    var cropTop = minRow
    
    if (cropLeft + size > boardSize) {
        cropLeft = boardSize - size
    }
    if (cropTop + size > boardSize) {
        cropTop = boardSize - size
    }
    cropLeft = maxOf(0, cropLeft)
    cropTop = maxOf(0, cropTop)
    
    // 如果裁剪后尺寸与原棋盘一样大，就不裁剪
    val finalSize = minOf(size, boardSize)
    
    return Triple(cropLeft, cropTop, finalSize)
}
