package com.wangyu.gotsumego.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException

class ProblemRepository(private val context: Context) {
    
    private var problems: List<Problem>? = null
    private val gson = Gson()
    
    fun loadProblems(): List<Problem> {
        problems?.let { return it }
        
        val json = loadJsonFromAssets()
        if (json.isNullOrEmpty()) {
            problems = emptyList()
            return emptyList()
        }
        
        try {
            val type = object : TypeToken<List<JsonProblem>>() {}.type
            val jsonProblems: List<JsonProblem> = gson.fromJson(json, type)
            problems = jsonProblems.map { it.toProblem() }
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
        if (problems == null) loadProblems()
        return problems ?: emptyList()
    }
    
    fun getProblemsByType(type: ProblemType): List<Problem> =
        getAllProblems().filter { it.type == type }
    
    fun getProblemsByBook(book: String): List<Problem> =
        getAllProblems().filter { it.book == book }
    
    fun getBooks(): List<String> =
        getAllProblems().map { it.book }.distinct().sorted()
    
    fun getBookStatistics(): Map<String, Int> =
        getAllProblems().groupBy { it.book }.mapValues { it.value.size }
            .entries.sortedByDescending { it.value }.associate { it.key to it.value }
    
    fun getProblemsByDifficulty(difficulty: Int): List<Problem> =
        getAllProblems().filter { it.difficulty == difficulty }
    
    fun getTotalCount(): Int = getAllProblems().size
}
