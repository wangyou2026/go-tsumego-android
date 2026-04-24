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
    
    /**
     * 从assets读取JSON文件
     */
    private fun loadJsonFromAssets(): String? {
        return try {
            context.assets.open("problems_full.json").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 获取所有题目
     */
    fun getAllProblems(): List<Problem> {
        if (problems == null) {
            loadProblems()
        }
        return problems ?: emptyList()
    }
    
    /**
     * 按类型获取题目
     */
    fun getProblemsByType(type: ProblemType): List<Problem> {
        return getAllProblems().filter { it.type == type }
    }
    
    /**
     * 按难度获取题目
     */
    fun getProblemsByDifficulty(difficulty: Int): List<Problem> {
        return getAllProblems().filter { it.difficulty == difficulty }
    }
    
    /**
     * 按类型和难度获取题目
     */
    fun getProblemsByTypeAndDifficulty(type: ProblemType, difficulty: Int): List<Problem> {
        return getAllProblems().filter { 
            it.type == type && it.difficulty == difficulty 
        }
    }
    
    /**
     * 获取题目的难度列表（已排序）
     */
    fun getDifficultyLevels(): List<Int> {
        return getAllProblems()
            .map { it.difficulty }
            .distinct()
            .sorted()
    }
    
    /**
     * 获取题目的类型统计
     */
    fun getTypeStatistics(): Map<ProblemType, Int> {
        return getAllProblems()
            .groupBy { it.type }
            .mapValues { it.value.size }
    }
    
    /**
     * 获取指定类型的题目数量
     */
    fun getProblemCountByType(type: ProblemType): Int {
        return getAllProblems().count { it.type == type }
    }
    
    /**
     * 获取指定难度的题目数量
     */
    fun getProblemCountByDifficulty(difficulty: Int): Int {
        return getAllProblems().count { it.difficulty == difficulty }
    }
    
    /**
     * 根据索引获取题目
     */
    fun getProblemAt(index: Int, type: ProblemType? = null): Problem? {
        val filtered = if (type != null) {
            getProblemsByType(type)
        } else {
            getAllProblems()
        }
        return filtered.getOrNull(index)
    }
    
    /**
     * 获取题目的索引
     */
    fun getProblemIndex(problem: Problem, type: ProblemType? = null): Int {
        val filtered = if (type != null) {
            getProblemsByType(type)
        } else {
            getAllProblems()
        }
        return filtered.indexOfFirst { it.id == problem.id }
    }
    
    /**
     * 获取题目总数
     */
    fun getTotalCount(): Int {
        return getAllProblems().size
    }
}
