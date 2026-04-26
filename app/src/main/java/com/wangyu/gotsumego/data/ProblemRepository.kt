package com.wangyu.gotsumego.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

/**
 * 题目数据仓库
 * 负责从JSON文件加载和访问题目数据
 */
class ProblemRepository(private val context: Context) {
    
    private var problems: List<Problem>? = null
    private var jsonProblems: List<JsonProblem>? = null
    private val gson = Gson()
    
    /**
     * 加载所有题目
     */
    fun loadProblems(): List<Problem> {
        problems?.let { return it }
        
        val json = loadJsonFromAssets()
        if (json.isNullOrEmpty()) {
            problems = emptyList()
            return emptyList()
        }
        
        try {
            val type = object : TypeToken<List<JsonProblem>>() {}.type
            jsonProblems = gson.fromJson(json, type)
            problems = jsonProblems?.map { it.toProblem() } ?: emptyList()
            return problems ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            problems = emptyList()
            return emptyList()
        }
    }
    
    private fun loadJsonFromAssets(): String? {
        return try {
            context.assets.open("problems_full.json").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
    
    fun getAllProblems(): List<Problem> {
        if (problems == null) {
            loadProblems()
        }
        return problems ?: emptyList()
    }
    
    fun getProblemsByType(type: ProblemType): List<Problem> {
        return getAllProblems().filter { it.type == type }
    }
    
    /**
     * 按书籍获取题目
     */
    fun getProblemsByBook(book: String): List<Problem> {
        return getAllProblems().filter { it.book == book }
    }
    
    /**
     * 获取所有书籍列表
     */
    fun getBooks(): List<String> {
        return getAllProblems()
            .map { it.book }
            .distinct()
            .sorted()
    }
    
    /**
     * 获取书籍统计
     */
    fun getBookStatistics(): Map<String, Int> {
        return getAllProblems()
            .groupBy { it.book }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .toMap()
    }
    
    fun getProblemsByDifficulty(difficulty: Int): List<Problem> {
        return getAllProblems().filter { it.difficulty == difficulty }
    }
    
    fun getProblemsByTypeAndDifficulty(type: ProblemType, difficulty: Int): List<Problem> {
        return getAllProblems().filter { 
            it.type == type && it.difficulty == difficulty 
        }
    }
    
    fun getDifficultyLevels(): List<Int> {
        return getAllProblems()
            .map { it.difficulty }
            .distinct()
            .sorted()
    }
    
    fun getTypeStatistics(): Map<ProblemType, Int> {
        return getAllProblems()
            .groupBy { it.type }
            .mapValues { it.value.size }
    }
    
    fun getProblemCountByType(type: ProblemType): Int {
        return getAllProblems().count { it.type == type }
    }
    
    fun getProblemCountByDifficulty(difficulty: Int): Int {
        return getAllProblems().count { it.difficulty == difficulty }
    }
    
    fun getProblemAt(index: Int, type: ProblemType? = null, book: String? = null): Problem? {
        val filtered = when {
            book != null -> getProblemsByBook(book)
            type != null -> getProblemsByType(type)
            else -> getAllProblems()
        }
        return filtered.getOrNull(index)
    }
    
    fun getProblemIndex(problem: Problem, type: ProblemType? = null, book: String? = null): Int {
        val filtered = when {
            book != null -> getProblemsByBook(book)
            type != null -> getProblemsByType(type)
            else -> getAllProblems()
        }
        return filtered.indexOfFirst { it.id == problem.id }
    }
    
    fun getTotalCount(): Int {
        return getAllProblems().size
    }
}
