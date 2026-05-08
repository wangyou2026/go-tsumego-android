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
    val book: String
) {
    // 固定13路棋盘，不再需要局部放大
    companion object {
        const val BOARD_SIZE = 13
    }
    
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
 * 将JSON格式的题目转换为Problem对象
 * 
 * 新版数据已经是13路坐标系：
 * - stones的y坐标：y=0是顶部，直接使用
 * - answer/solutionMoves的y坐标：y=0是顶部，直接使用
 * - 不再需要任何坐标翻转
 */
fun JsonProblem.toProblem(): Problem {
    // 新数据已经是13路坐标系，坐标直接使用，无需翻转
    val boardSize = 13  // 固定13路
    
    val stoneList = stones.mapNotNull { stoneData ->
        if (stoneData.size >= 3) {
            Stone(
                col = stoneData[0],
                row = stoneData[1],  // 直接使用，已经是13路坐标系
                color = StoneColor.fromValue(stoneData[2])
            )
        } else null
    }
    
    // answer/solutionMoves也直接使用，无需翻转
    val moves = if (answer.size >= 2) {
        listOf(Position(col = answer[0], row = answer[1]))
    } else emptyList()
    
    val solutionMoveList = solutionMoves?.mapNotNull { move ->
        if (move.size >= 3) {
            SolutionMove(
                col = move[0],
                row = move[1],  // 直接使用，已经是13路坐标系
                color = StoneColor.fromValue(move[2])
            )
        } else null
    } ?: emptyList()
    
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
        book = book ?: "其他"
    )
}
