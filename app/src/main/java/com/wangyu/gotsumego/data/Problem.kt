package com.wangyu.gotsumego.data

/**
 * 围棋棋子颜色
 */
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

/**
 * 题目类型
 */
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

/**
 * 围棋题目数据模型
 */
data class Problem(
    val id: Int,
    val type: ProblemType,
    val difficulty: Int,
    val title: String,
    val boardSize: Int,
    val stones: List<Stone>,
    val toPlay: StoneColor,
    val correctMoves: List<Position>,
    val hint: String?,
    val solutionComment: String?,
    val book: String
) {
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
    
    val isSingleMove: Boolean
        get() = correctMoves.size == 1
    
    val difficultyName: String
        get() = when (difficulty) {
            1 -> "入门"
            2 -> "初级"
            3 -> "中级"
            4 -> "高级"
            5 -> "专业"
            else -> "未知"
        }
}

data class Position(
    val col: Int,
    val row: Int
) {
    fun toIndex(boardSize: Int): Int {
        return row * boardSize + col
    }
    
    companion object {
        fun fromIndex(index: Int, boardSize: Int): Position {
            return Position(
                col = index % boardSize,
                row = index / boardSize
            )
        }
        
        fun fromJsonCoords(x: Int, y: Int, boardSize: Int): Position {
            return Position(col = x, row = boardSize - 1 - y)
        }
    }
}

data class Stone(
    val col: Int,
    val row: Int,
    val color: StoneColor
)

/**
 * 从JsonProblem转换为Problem
 */
fun JsonProblem.toProblem(): Problem {
    val stoneList = stones.mapNotNull { stoneData ->
        if (stoneData.size >= 3) {
            val x = stoneData[0]
            val y = stoneData[1]
            val colorValue = stoneData[2]
            Stone(
                col = x,
                row = boardSize - 1 - y,
                color = StoneColor.fromValue(colorValue)
            )
        } else null
    }
    
    val moves = if (answer.size >= 2) {
        val x = answer[0]
        val y = answer[1]
        listOf(Position(col = x, row = boardSize - 1 - y))
    } else {
        emptyList()
    }
    
    val comment = solutions?.firstOrNull()?.comment
    
    return Problem(
        id = id,
        type = ProblemType.fromKey(type),
        difficulty = difficulty,
        title = title,
        boardSize = boardSize,
        stones = stoneList,
        toPlay = StoneColor.fromValue(toPlay),
        correctMoves = moves,
        hint = hint,
        solutionComment = comment,
        book = book ?: "其他"
    )
}
