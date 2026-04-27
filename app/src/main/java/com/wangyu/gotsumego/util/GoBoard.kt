package com.wangyu.gotsumego.util

import com.wangyu.gotsumego.data.Position
import com.wangyu.gotsumego.data.StoneColor

/**
 * 围棋棋盘工具类
 * 提供棋盘相关的计算和转换功能
 * 包含提子规则实现
 */
object GoBoard {
    
    fun getStarPoints(boardSize: Int): List<Position> {
        return when (boardSize) {
            9 -> listOf(
                Position(2, 2), Position(6, 2), Position(4, 4),
                Position(2, 6), Position(6, 6)
            )
            13 -> listOf(
                Position(3, 3), Position(9, 3), Position(6, 6),
                Position(3, 9), Position(9, 9)
            )
            19 -> listOf(
                Position(3, 3), Position(9, 3), Position(15, 3),
                Position(3, 9), Position(9, 9), Position(15, 9),
                Position(3, 15), Position(9, 15), Position(15, 15)
            )
            else -> emptyList()
        }
    }
    
    fun isStarPoint(col: Int, row: Int, boardSize: Int): Boolean {
        return getStarPoints(boardSize).any { it.col == col && it.row == row }
    }
    
    fun isValidPosition(col: Int, row: Int, boardSize: Int): Boolean {
        return col in 0 until boardSize && row in 0 until boardSize
    }
    
    fun isValidIndex(index: Int, boardSize: Int): Boolean {
        return index in 0 until (boardSize * boardSize)
    }
    
    fun toIndex(col: Int, row: Int, boardSize: Int): Int = row * boardSize + col
    
    fun toPosition(index: Int, boardSize: Int): Position = Position(
        col = index % boardSize,
        row = index / boardSize
    )
    
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
    
    fun boardToString(board: Array<Array<StoneColor>>): String {
        val sb = StringBuilder()
        for (row in board) {
            for (stone in row) {
                sb.append(stone.symbol)
            }
        }
        return sb.toString()
    }
    
    fun getAdjacentIndices(index: Int, boardSize: Int): List<Int> {
        val col = index % boardSize
        val row = index / boardSize
        val adjacent = mutableListOf<Int>()
        
        if (col > 0) adjacent.add(index - 1)           // 左
        if (col < boardSize - 1) adjacent.add(index + 1)  // 右
        if (row > 0) adjacent.add(index - boardSize)      // 上
        if (row < boardSize - 1) adjacent.add(index + boardSize)  // 下
        
        return adjacent
    }
    
    /**
     * 找到与指定位置相连的同色棋子群
     */
    fun getConnectedGroup(boardString: String, index: Int, boardSize: Int): Set<Int> {
        val color = getStoneAt(boardString, index)
        if (color == StoneColor.EMPTY) return emptySet()
        
        val group = mutableSetOf<Int>()
        val toCheck = mutableListOf(index)
        
        while (toCheck.isNotEmpty()) {
            val current = toCheck.removeAt(0)
            if (current in group) continue
            
            if (getStoneAt(boardString, current) == color) {
                group.add(current)
                for (adj in getAdjacentIndices(current, boardSize)) {
                    if (adj !in group && getStoneAt(boardString, adj) == color) {
                        toCheck.add(adj)
                    }
                }
            }
        }
        
        return group
    }
    
    /**
     * 计算一个棋子群的气数
     */
    fun getGroupLiberties(boardString: String, group: Set<Int>, boardSize: Int): List<Int> {
        val liberties = mutableSetOf<Int>()
        
        for (index in group) {
            for (adj in getAdjacentIndices(index, boardSize)) {
                if (getStoneAt(boardString, adj) == StoneColor.EMPTY) {
                    liberties.add(adj)
                }
            }
        }
        
        return liberties.toList()
    }
    
    /**
     * 检查并移除没有气的棋子（提子）
     */
    fun captureStones(
        boardString: String, 
        lastMoveIndex: Int, 
        boardSize: Int
    ): Pair<String, Int> {
        val lastColor = getStoneAt(boardString, lastMoveIndex)
        if (lastColor == StoneColor.EMPTY) return Pair(boardString, 0)
        
        val opponentColor = if (lastColor == StoneColor.BLACK) StoneColor.WHITE else StoneColor.BLACK
        var newBoard = boardString
        var totalCaptured = 0
        
        val checkedGroups = mutableSetOf<Int>()
        
        for (adj in getAdjacentIndices(lastMoveIndex, boardSize)) {
            if (adj in checkedGroups) continue
            if (getStoneAt(newBoard, adj) != opponentColor) continue
            
            val group = getConnectedGroup(newBoard, adj, boardSize)
            checkedGroups.addAll(group)
            
            val liberties = getGroupLiberties(newBoard, group, boardSize)
            
            if (liberties.isEmpty()) {
                val chars = newBoard.toCharArray()
                for (idx in group) {
                    chars[idx] = StoneColor.EMPTY.symbol
                }
                newBoard = String(chars)
                totalCaptured += group.size
            }
        }
        
        return Pair(newBoard, totalCaptured)
    }
    
    /**
     * 在指定位置放置棋子，并自动处理提子
     */
    fun placeStone(
        boardString: String, 
        index: Int, 
        color: StoneColor, 
        boardSize: Int
    ): String {
        if (!isValidIndex(index, boardSize)) return boardString
        if (!isEmptyAt(boardString, index)) return boardString
        
        // 1. 放置棋子
        val chars = boardString.toCharArray()
        chars[index] = color.symbol
        var newBoard = String(chars)
        
        // 2. 检查并提子
        val (boardAfterCapture, _) = captureStones(newBoard, index, boardSize)
        newBoard = boardAfterCapture
        
        // 3. 检查自杀（如果自己没气了，恢复原状）
        val myGroup = getConnectedGroup(newBoard, index, boardSize)
        val myLiberties = getGroupLiberties(newBoard, myGroup, boardSize)
        
        if (myLiberties.isEmpty()) {
            return boardString
        }
        
        return newBoard
    }
    
    fun removeStone(boardString: String, index: Int, boardSize: Int): String {
        if (!isValidIndex(index, boardSize)) return boardString
        
        val chars = boardString.toCharArray()
        chars[index] = StoneColor.EMPTY.symbol
        return String(chars)
    }
    
    fun getStoneAt(boardString: String, index: Int): StoneColor {
        if (index < 0 || index >= boardString.length) return StoneColor.EMPTY
        return when (boardString[index]) {
            'X' -> StoneColor.BLACK
            'O' -> StoneColor.WHITE
            else -> StoneColor.EMPTY
        }
    }
    
    fun isEmptyAt(boardString: String, index: Int): Boolean {
        return getStoneAt(boardString, index) == StoneColor.EMPTY
    }
    
    fun getAdjacentPositions(col: Int, row: Int, boardSize: Int): List<Position> {
        val positions = mutableListOf<Position>()
        
        if (col > 0) positions.add(Position(col - 1, row))
        if (col < boardSize - 1) positions.add(Position(col + 1, row))
        if (row > 0) positions.add(Position(col, row - 1))
        if (row < boardSize - 1) positions.add(Position(col, row + 1))
        
        return positions
    }
}
