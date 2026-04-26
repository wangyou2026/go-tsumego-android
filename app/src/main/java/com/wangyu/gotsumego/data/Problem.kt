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
    // 裁剪相关字段
    val cropLeft: Int,
    val cropRight: Int,
    val cropTop: Int,
    val cropBottom: Int,
    val cropSize: Int
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
    
    /**
     * 生成裁剪后的棋盘字符串
     */
    fun toCroppedBoardString(): String {
        if (cropSize <= 0 || cropLeft >= cropRight || cropTop >= cropBottom) {
            return toBoardString()
        }
        
        val sb = StringBuilder()
        for (row in cropTop..cropBottom) {
            for (col in cropLeft..cropRight) {
                val stone = stones.find { it.row == row && it.col == col }
                sb.append(stone?.color?.symbol ?: StoneColor.EMPTY.symbol)
            }
        }
        return sb.toString()
    }
    
    /**
     * 将全局坐标转换为裁剪后棋盘的坐标
     */
    fun globalToCropped(col: Int, row: Int): Pair<Int, Int> {
        return Pair(col - cropLeft, row - cropTop)
    }
    
    /**
     * 将裁剪后棋盘的坐标转换为全局坐标
     */
    fun croppedToGlobal(croppedCol: Int, croppedRow: Int): Pair<Int, Int> {
        return Pair(croppedCol + cropLeft, croppedRow + cropTop)
    }
    
    val firstCorrectMove: Position?
        get() = correctMoves.firstOrNull()
    
    val difficultyName: String
        get() = when (difficulty) {
            1 -> "入门"
            2 -> "初级"
            3 -> "中级"
            4 -> "高级"
            5 -> "专业"
            else -> "未知"
        }
    
    val isCropped: Boolean
        get() = cropSize > 0 && cropSize < boardSize
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
    
    // 解析完整解答序列
    val solutionMoveList = solutionMoves?.mapNotNull { move ->
        if (move.size >= 3) {
            SolutionMove(
                col = move[0],
                row = boardSize - 1 - move[1],
                color = StoneColor.fromValue(move[2])
            )
        } else null
    } ?: emptyList()
    
    // 裁剪参数
    val cLeft = cropLeft ?: 0
    val cRight = cropRight ?: (boardSize - 1)
    val cTop = cropTop ?: 0
    val cBottom = cropBottom ?: (boardSize - 1)
    val cSize = cropSize ?: boardSize
    
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
        cropLeft = cLeft,
        cropRight = cRight,
        cropTop = cTop,
        cropBottom = cBottom,
        cropSize = cSize
    )
}
