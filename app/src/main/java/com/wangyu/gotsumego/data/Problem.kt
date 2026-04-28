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
    // 局部放大区域（棋盘坐标）
    val zoomMinCol: Int,
    val zoomMaxCol: Int,
    val zoomMinRow: Int,
    val zoomMaxRow: Int
) {
    /**
     * 是否需要局部放大显示
     * 当棋子分布范围小于棋盘大小时启用
     */
    val shouldZoom: Boolean
        get() = (zoomMaxCol - zoomMinCol + 1) < boardSize || (zoomMaxRow - zoomMinRow + 1) < boardSize
    
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
    val stoneList = stones.mapNotNull { stoneData ->
        if (stoneData.size >= 3) {
            Stone(
                col = stoneData[0],
                row = boardSize - 1 - stoneData[1],
                color = StoneColor.fromValue(stoneData[2])
            )
        } else null
    }
    
    val moves = if (answer.size >= 2) {
        listOf(Position(col = answer[0], row = boardSize - 1 - answer[1]))
    } else emptyList()
    
    val solutionMoveList = solutionMoves?.mapNotNull { move ->
        if (move.size >= 3) {
            SolutionMove(
                col = move[0],
                row = boardSize - 1 - move[1],
                color = StoneColor.fromValue(move[2])
            )
        } else null
    } ?: emptyList()
    
    // 计算局部放大区域
    val (zoomMinCol, zoomMaxCol, zoomMinRow, zoomMaxRow) = calculateZoomArea(stoneList, boardSize)
    
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
        zoomMinCol = zoomMinCol,
        zoomMaxCol = zoomMaxCol,
        zoomMinRow = zoomMinRow,
        zoomMaxRow = zoomMaxRow
    )
}

/**
 * 计算局部放大区域
 * 返回 (minCol, maxCol, minRow, maxRow)
 */
private fun calculateZoomArea(stones: List<Stone>, boardSize: Int): Tuple4<Int, Int, Int, Int> {
    if (stones.isEmpty()) return Tuple4(0, boardSize - 1, 0, boardSize - 1)
    
    // 找到棋子的边界
    val cols = stones.map { it.col }
    val rows = stones.map { it.row }
    
    var minCol = cols.min()
    var maxCol = cols.max()
    var minRow = rows.min()
    var maxRow = rows.max()
    
    // 添加边距（2格）
    val margin = 2
    minCol = maxOf(0, minCol - margin)
    maxCol = minOf(boardSize - 1, maxCol + margin)
    minRow = maxOf(0, minRow - margin)
    maxRow = minOf(boardSize - 1, maxRow + margin)
    
    return Tuple4(minCol, maxCol, minRow, maxRow)
}

/**
 * 四元组数据类
 */
data class Tuple4<T1, T2, T3, T4>(
    val first: T1,
    val second: T2,
    val third: T3,
    val fourth: T4
)
