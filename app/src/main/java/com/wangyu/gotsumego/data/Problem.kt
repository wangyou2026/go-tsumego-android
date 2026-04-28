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
    // SGF 格式：row 0 在底部，answer/solutionMoves 已翻转（顶部=0）
    // Android 显示：row 0 在顶部
    // 所以需要将 answer/solutionMoves 的 row 翻转回来以匹配 stones
    val boardSize = this.boardSize
    val flippedRow: (Int) -> Int = { y -> boardSize - 1 - y }
    
    val stoneList = stones.mapNotNull { stoneData ->
        if (stoneData.size >= 3) {
            Stone(
                col = stoneData[0],
                row = stoneData[1],  // stones 的 row 是 SGF 原生坐标
                color = StoneColor.fromValue(stoneData[2])
            )
        } else null
    }
    
    // answer/solutionMoves 的 row 已翻转，需要翻转回来以匹配 stones
    val moves = if (answer.size >= 2) {
        listOf(Position(col = answer[0], row = flippedRow(answer[1])))
    } else emptyList()
    
    // solutionMoves: [col, row, color] 格式，row 需要翻转
    val solutionMoveList = solutionMoves?.mapNotNull { move ->
        if (move.size >= 3) {
            SolutionMove(
                col = move[0],
                row = flippedRow(move[1]),  // 翻转 row 以匹配 stones
                color = StoneColor.fromValue(move[2])
            )
        } else null
    } ?: emptyList()
    
    // 计算局部放大区域（同时考虑初始棋子和答案/着法）
    val (zoomMinCol, zoomMaxCol, zoomMinRow, zoomMaxRow) = calculateZoomArea(this)
    
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
 * 计算局部放大区域（同时包含初始棋子和答案/着法）
 * 策略：
 * 1. 始终包含所有初始棋子和答案/着法
 * 2. 优先以初始棋子区域为主（这是用户看到的主要区域）
 * 3. 确保答案在可见范围内
 * 返回 (minCol, maxCol, minRow, maxRow)
 */
private fun calculateZoomArea(problem: JsonProblem): Tuple4<Int, Int, Int, Int> {
    val stones = problem.stones
    if (stones.isEmpty()) return Tuple4(0, problem.boardSize - 1, 0, problem.boardSize - 1)

    val boardSize = problem.boardSize
    val margin = 3  // 稍微增加边距

    // 初始棋子坐标
    val stoneCols = stones.mapNotNull { if (it.size >= 1) it.get(0) else null }
    val stoneRows = stones.mapNotNull { if (it.size >= 2) it.get(1) else null }
    
    val answer = problem.answer
    val sm = problem.solutionMoves ?: emptyList()
    
    // 收集答案+前3手着法坐标（答案是第一位的）
    val answerCols = mutableListOf<Int>()
    val answerRows = mutableListOf<Int>()
    if (answer.size >= 2) {
        answerCols.add(answer[0])
        answerRows.add(answer[1])
    }
    for (move in sm.take(3)) {
        if (move.size >= 2) {
            answerCols.add(move[0])
            answerRows.add(move[1])
        }
    }

    // 如果没有答案数据，返回包含初始棋子的区域
    if (answerCols.isEmpty()) {
        val minCol = maxOf(0, stoneCols.minOrNull()!! - margin)
        val maxCol = minOf(boardSize - 1, stoneCols.maxOrNull()!! + margin)
        val minRow = maxOf(0, stoneRows.minOrNull()!! - margin)
        val maxRow = minOf(boardSize - 1, stoneRows.maxOrNull()!! + margin)
        return Tuple4(minCol, maxCol, minRow, maxRow)
    }

    // 以初始棋子区域为基础
    var minCol = stoneCols.minOrNull()!!
    var maxCol = stoneCols.maxOrNull()!!
    var minRow = stoneRows.minOrNull()!!
    var maxRow = stoneRows.maxOrNull()!!
    
    // 确保答案在可见范围内
    val answerMinCol = answerCols.minOrNull()!!
    val answerMaxCol = answerCols.maxOrNull()!!
    val answerMinRow = answerRows.minOrNull()!!
    val answerMaxRow = answerRows.maxOrNull()!!
    
    if (answerMinCol < minCol) minCol = answerMinCol
    if (answerMaxCol > maxCol) maxCol = answerMaxCol
    if (answerMinRow < minRow) minRow = answerMinRow
    if (answerMaxRow > maxRow) maxRow = answerMaxRow
    
    // 加上边距
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
