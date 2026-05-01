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

/**
 * 自动检测answer/solutionMoves的y坐标是否需要翻转
 * 
 * 不同数据源的坐标约定不同：
 * - 有些数据：answer的y=0是顶部（不需要翻转）
 * - 有些数据：answer的y=0是底部（需要翻转，和stones一样）
 * 
 * 检测方法：构建棋盘后，看answer位置是否在空位上且靠近棋子
 */
private fun needsAnswerYFlip(jsonProblem: JsonProblem): Boolean {
    val boardSize = jsonProblem.boardSize
    val stones = jsonProblem.stones
    val answer = jsonProblem.answer
    
    if (answer.size < 2 || stones.isEmpty()) return false
    
    // 构建棋盘（stones始终y翻转）
    val board = CharArray(boardSize * boardSize) { '.' }
    val stoneRows = mutableListOf<Int>()
    for (s in stones) {
        if (s.size < 3) continue
        val col = s[0]
        val row = boardSize - 1 - s[1]  // stones y翻转
        val symbol = if (s[2] == 1) 'X' else 'O'
        val idx = row * boardSize + col
        if (idx in board.indices) {
            board[idx] = symbol
            stoneRows.add(row)
        }
    }
    
    val ansCol = answer[0]
    val ansRowNoFlip = answer[1]
    val ansRowFlip = boardSize - 1 - answer[1]
    
    val idxNoFlip = ansRowNoFlip * boardSize + ansCol
    val idxFlip = ansRowFlip * boardSize + ansCol
    
    val noFlipEmpty = idxNoFlip in board.indices && board[idxNoFlip] == '.'
    val flipEmpty = idxFlip in board.indices && board[idxFlip] == '.'
    
    // 只有一个方向是空位
    if (noFlipEmpty && !flipEmpty) return false
    if (flipEmpty && !noFlipEmpty) return true
    
    // 两个方向都是空位，看哪个更靠近棋子
    if (noFlipEmpty && flipEmpty && stoneRows.isNotEmpty()) {
        val midRow = (stoneRows.minOrNull()!! + stoneRows.maxOrNull()!!) / 2.0
        val distNoFlip = kotlin.math.abs(ansRowNoFlip - midRow)
        val distFlip = kotlin.math.abs(ansRowFlip - midRow)
        return distFlip < distNoFlip
    }
    
    // 默认不翻转
    return false
}

fun JsonProblem.toProblem(): Problem {
    // JSON 数据中：
    // - stones 的 y 坐标：0=底部，需要翻转 → row = boardSize - 1 - y
    // - answer/solutionMoves 的 y 坐标：自动检测是否需要翻转
    //   不同数据源约定不同，通过检查answer位置是否在棋子附近来判断
    val boardSize = this.boardSize
    
    val stoneList = stones.mapNotNull { stoneData ->
        if (stoneData.size >= 3) {
            Stone(
                col = stoneData[0],
                row = boardSize - 1 - stoneData[1],  // stones: y从底部开始，需翻转
                color = StoneColor.fromValue(stoneData[2])
            )
        } else null
    }
    
    // 自动检测answer/solutionMoves是否需要y翻转
    val flipAnswerY = needsAnswerYFlip(this)
    
    val moves = if (answer.size >= 2) {
        val ansRow = if (flipAnswerY) boardSize - 1 - answer[1] else answer[1]
        listOf(Position(col = answer[0], row = ansRow))
    } else emptyList()
    
    val solutionMoveList = solutionMoves?.mapNotNull { move ->
        if (move.size >= 3) {
            val moveRow = if (flipAnswerY) boardSize - 1 - move[1] else move[1]
            SolutionMove(
                col = move[0],
                row = moveRow,
                color = StoneColor.fromValue(move[2])
            )
        } else null
    } ?: emptyList()
    
    // 计算局部放大区域（使用转换后的坐标，包含所有解答步骤）
    val (zoomMinCol, zoomMaxCol, zoomMinRow, zoomMaxRow) = calculateZoomArea(stoneList, moves, solutionMoveList, boardSize)
    
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
 * 计算局部放大区域（使用转换后的棋盘坐标）
 * 同时包含初始棋子和所有解答步骤
 * 返回 (minCol, maxCol, minRow, maxRow)
 */
private fun calculateZoomArea(
    stones: List<Stone>,
    correctMoves: List<Position>,
    solutionMoves: List<SolutionMove>,
    boardSize: Int
): Tuple4<Int, Int, Int, Int> {
    if (stones.isEmpty()) return Tuple4(0, boardSize - 1, 0, boardSize - 1)

    val margin = 2

    // 收集所有坐标（已转换到棋盘坐标系）
    val allCols = mutableListOf<Int>()
    val allRows = mutableListOf<Int>()
    
    for (stone in stones) {
        allCols.add(stone.col)
        allRows.add(stone.row)
    }
    for (move in correctMoves) {
        allCols.add(move.col)
        allRows.add(move.row)
    }
    // 包含所有解答步骤（不只是前3步），确保提示标记在可见区域内
    for (move in solutionMoves) {
        allCols.add(move.col)
        allRows.add(move.row)
    }

    val minCol = maxOf(0, allCols.minOrNull()!! - margin)
    val maxCol = minOf(boardSize - 1, allCols.maxOrNull()!! + margin)
    val minRow = maxOf(0, allRows.minOrNull()!! - margin)
    val maxRow = minOf(boardSize - 1, allRows.maxOrNull()!! + margin)

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
