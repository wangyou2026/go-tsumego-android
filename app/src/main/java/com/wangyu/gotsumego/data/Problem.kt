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
enum class ProblemType(val key: String, val displayName: String) {
    LIFE_DEATH("life_death", "死活题"),
    TESUJI("tesuji", "手筋题"),
    YOSE("yose", "官子题"),
    CAPTURE("capture", "吃子题"),
    UNKNOWN("unknown", "其他");
    
    companion object {
        fun fromKey(key: String): ProblemType {
            return entries.find { it.key == key } ?: UNKNOWN
        }
    }
}

/**
 * 围棋题目数据模型
 * 
 * 坐标系统说明：
 * - JSON中 stones: [[x, y, color]]
 *   - x = 列 (col)，0-based，从左到右递增
 *   - y = 行 (row)，0-based，从上到下递增
 * - 棋盘显示时：
 *   - row = y (0在顶部)
 *   - col = x (0在左边)
 * - 落子位置用 index 表示：
 *   - index = row * boardSize + col
 *   - 即 index = y * boardSize + x
 * 
 * 棋盘字符串格式：
 * - 长度 = boardSize * boardSize
 * - '.' = 空位
 * - 'X' = 黑子
 * - 'O' = 白子
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
    val solutionComment: String?
) {
    /**
     * 将题目转换为棋盘字符串
     * 字符串长度 = boardSize * boardSize
     * index = row * boardSize + col
     */
    fun toBoardString(): String {
        val sb = StringBuilder()
        // 棋盘字符串长度必须是 boardSize * boardSize
        repeat(boardSize * boardSize) { index ->
            val row = index / boardSize
            val col = index % boardSize
            val stone = stones.find { it.row == row && it.col == col }
            sb.append(stone?.color?.symbol ?: StoneColor.EMPTY.symbol)
        }
        return sb.toString()
    }
    
    /**
     * 获取正解的第一个位置（用于单步题目）
     */
    val firstCorrectMove: Position?
        get() = correctMoves.firstOrNull()
    
    /**
     * 检查是否是单步题（只有一个正解）
     */
    val isSingleMove: Boolean
        get() = correctMoves.size == 1
}

/**
 * 棋子位置
 * @param col 列(0-based，从左到右)
 * @param row 行(0-based，从上到下)
 */
data class Position(
    val col: Int,
    val row: Int
) {
    /**
     * 转换为index
     * index = row * boardSize + col
     */
    fun toIndex(boardSize: Int): Int {
        return row * boardSize + col
    }
    
    companion object {
        /**
         * 从index转换为Position
         * row = index / boardSize
         * col = index % boardSize
         */
        fun fromIndex(index: Int, boardSize: Int): Position {
            return Position(
                col = index % boardSize,
                row = index / boardSize
            )
        }
        
        /**
         * 从JSON格式的坐标创建Position
         * JSON格式: [x, y] 其中 x=col, y=row
         */
        fun fromJsonCoords(x: Int, y: Int): Position {
            return Position(col = x, row = y)
        }
    }
}

/**
 * 棋盘上的棋子
 */
data class Stone(
    val col: Int,      // 列 (x坐标)
    val row: Int,      // 行 (y坐标)
    val color: StoneColor
)

/**
 * 从JsonProblem转换为Problem
 */
fun JsonProblem.toProblem(): Problem {
    // JSON坐标: y=0在棋盘底部，y增加向上
    // 棋盘绘制: row=0在顶部，row增加向下
    // 需要反转y坐标: row = boardSize - 1 - y
    val stoneList = stones.mapNotNull { stoneData ->
        if (stoneData.size >= 3) {
            val x = stoneData[0] // 列
            val y = stoneData[1] // JSON中的y（从底部开始）
            val colorValue = stoneData[2]
            Stone(
                col = x,
                row = boardSize - 1 - y, // 反转y坐标
                color = StoneColor.fromValue(colorValue)
            )
        } else null
    }
    
    // 转换正解位置，同样需要反转y坐标
    val moves = if (answer.size >= 2) {
        val x = answer[0]
        val y = answer[1]
        listOf(Position(col = x, row = boardSize - 1 - y))
    } else {
        emptyList()
    }
    
    // 从solutions中提取评论
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
        solutionComment = comment
    )
}
