package com.wangyu.gotsumego.util

import com.wangyu.gotsumego.data.Position
import com.wangyu.gotsumego.data.StoneColor

/**
 * 围棋棋盘工具类
 * 提供棋盘相关的计算和转换功能
 */
object GoBoard {
    
    /**
     * 计算棋盘上的星位（天元和星位点）
     * @param boardSize 棋盘大小 (9, 13, 19)
     * @return 星位位置的列表
     */
    fun getStarPoints(boardSize: Int): List<Position> {
        return when (boardSize) {
            9 -> listOf(
                Position(2, 2),  // 左上
                Position(6, 2),  // 右上
                Position(4, 4),  // 天元
                Position(2, 6),  // 左下
                Position(6, 6)   // 右下
            )
            13 -> listOf(
                Position(3, 3),  // 左上
                Position(9, 3),  // 右上
                Position(6, 6),  // 天元
                Position(3, 9),  // 左下
                Position(9, 9)   // 右下
            )
            19 -> listOf(
                Position(3, 3),  // 左上
                Position(9, 3),  // 上边中
                Position(15, 3), // 右上
                Position(3, 9),  // 左边中
                Position(9, 9),  // 天元
                Position(15, 9), // 右边中
                Position(3, 15), // 左下
                Position(9, 15), // 下边中
                Position(15, 15) // 右下
            )
            else -> emptyList()
        }
    }
    
    /**
     * 检查指定位置是否是星位
     */
    fun isStarPoint(col: Int, row: Int, boardSize: Int): Boolean {
        return getStarPoints(boardSize).any { it.col == col && it.row == row }
    }
    
    /**
     * 检查坐标是否在棋盘范围内
     */
    fun isValidPosition(col: Int, row: Int, boardSize: Int): Boolean {
        return col in 0 until boardSize && row in 0 until boardSize
    }
    
    /**
     * 检查index是否在棋盘范围内
     */
    fun isValidIndex(index: Int, boardSize: Int): Boolean {
        return index in 0 until (boardSize * boardSize)
    }
    
    /**
     * 将(col, row)转换为index
     * index = row * boardSize + col
     */
    fun toIndex(col: Int, row: Int, boardSize: Int): Int {
        return row * boardSize + col
    }
    
    /**
     * 将index转换为(col, row)
     * row = index / boardSize
     * col = index % boardSize
     */
    fun toPosition(index: Int, boardSize: Int): Position {
        return Position(
            col = index % boardSize,
            row = index / boardSize
        )
    }
    
    /**
     * 将棋盘字符串转换为二维数组
     * @param boardString 长度为 boardSize * boardSize 的字符串
     * @return 二维数组 [row][col]
     */
    fun stringToBoard(boardString: String, boardSize: Int): Array<Array<StoneColor>> {
        val board = Array(boardSize) { Array(boardSize) { StoneColor.EMPTY } }
        
        for (i in boardString.indices) {
            val row = i / boardSize
            val col = i % boardSize
            if (row < boardSize && col < boardSize) {
                board[row][col] = when (boardString[i]) {
                    'X' -> StoneColor.BLACK
                    'O' -> StoneColor.WHITE
                    else -> StoneColor.EMPTY
                }
            }
        }
        
        return board
    }
    
    /**
     * 将二维数组转换为棋盘字符串
     */
    fun boardToString(board: Array<Array<StoneColor>>): String {
        val sb = StringBuilder()
        for (row in board) {
            for (stone in row) {
                sb.append(stone.symbol)
            }
        }
        return sb.toString()
    }
    
    /**
     * 在指定位置放置棋子，返回新的棋盘状态
     */
    fun placeStone(
        boardString: String, 
        index: Int, 
        color: StoneColor, 
        boardSize: Int
    ): String {
        if (!isValidIndex(index, boardSize)) return boardString
        
        val chars = boardString.toCharArray()
        chars[index] = color.symbol
        return String(chars)
    }
    
    /**
     * 移除指定位置的棋子，返回新的棋盘状态
     */
    fun removeStone(boardString: String, index: Int, boardSize: Int): String {
        if (!isValidIndex(index, boardSize)) return boardString
        
        val chars = boardString.toCharArray()
        chars[index] = StoneColor.EMPTY.symbol
        return String(chars)
    }
    
    /**
     * 获取指定位置的棋子颜色
     */
    fun getStoneAt(boardString: String, index: Int): StoneColor {
        if (index < 0 || index >= boardString.length) return StoneColor.EMPTY
        return when (boardString[index]) {
            'X' -> StoneColor.BLACK
            'O' -> StoneColor.WHITE
            else -> StoneColor.EMPTY
        }
    }
    
    /**
     * 检查位置是否为空
     */
    fun isEmptyAt(boardString: String, index: Int): Boolean {
        return getStoneAt(boardString, index) == StoneColor.EMPTY
    }
    
    /**
     * 获取相邻位置
     */
    fun getAdjacentPositions(col: Int, row: Int, boardSize: Int): List<Position> {
        val positions = mutableListOf<Position>()
        
        if (col > 0) positions.add(Position(col - 1, row))
        if (col < boardSize - 1) positions.add(Position(col + 1, row))
        if (row > 0) positions.add(Position(col, row - 1))
        if (row < boardSize - 1) positions.add(Position(col, row + 1))
        
        return positions
    }
    
    /**
     * 获取位置的相邻index列表
     */
    fun getAdjacentIndices(index: Int, boardSize: Int): List<Int> {
        val col = index % boardSize
        val row = index / boardSize
        val adjacent = mutableListOf<Int>()
        
        if (col > 0) adjacent.add(index - 1)  // 左
        if (col < boardSize - 1) adjacent.add(index + 1)  // 右
        if (row > 0) adjacent.add(index - boardSize)  // 上
        if (row < boardSize - 1) adjacent.add(index + boardSize)  // 下
        
        return adjacent
    }
}
