package com.wangyu.gotsumego.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

class ProblemRepository(private val context: Context) {
    
    private var builtinProblems: List<Problem>? = null
    private var userProblems: MutableList<Problem> = mutableListOf()
    private val gson = Gson()
    private val userFile: File
        get() = File(context.filesDir, "user_problems.json")
    
    init {
        loadUserProblems()
    }
    
    fun loadProblems(): List<Problem> {
        if (builtinProblems == null) {
            val json = loadJsonFromAssets()
            if (json.isNullOrEmpty()) {
                builtinProblems = emptyList()
            } else {
                try {
                    val type = object : TypeToken<List<JsonProblem>>() {}.type
                    val jsonProblems: List<JsonProblem> = gson.fromJson(json, type)
                    builtinProblems = jsonProblems.map { it.toProblem() }
                } catch (e: Exception) {
                    e.printStackTrace()
                    builtinProblems = emptyList()
                }
            }
        }
        return (builtinProblems ?: emptyList()) + userProblems
    }
    
    private fun loadJsonFromAssets(): String? {
        loadCompressedJson()?.let { return it }
        return loadNormalJson()
    }
    
    private fun loadCompressedJson(): String? {
        return try {
            context.assets.open("problems_compressed.bin").use { inputStream ->
                GZIPInputStream(inputStream).use { gzip ->
                    BufferedReader(InputStreamReader(gzip, "UTF-8")).use { reader ->
                        reader.readText()
                    }
                }
            }
        } catch (e: Exception) { null }
    }
    
    private fun loadNormalJson(): String? {
        return try {
            context.assets.open("problems_full.json").bufferedReader().use { it.readText() }
        } catch (e: IOException) { e.printStackTrace(); null }
    }
    
    fun getAllProblems(): List<Problem> {
        if (builtinProblems == null) loadProblems()
        return (builtinProblems ?: emptyList()) + userProblems
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
    
    // === User-created problems ===
    
    fun getUserProblems(): List<Problem> = userProblems.toList()
    
    fun addUserProblem(problem: Problem) {
        // Find the next available ID
        val allIds = (builtinProblems ?: emptyList()).map { it.id } + userProblems.map { it.id }
        val nextId = (allIds.maxOrNull() ?: 0) + 1
        val newProblem = problem.copy(id = nextId)
        userProblems.add(newProblem)
        saveUserProblems()
    }
    
    fun addUserProblems(problems: List<Problem>) {
        val allIds = (builtinProblems ?: emptyList()).map { it.id } + userProblems.map { it.id }
        var nextId = (allIds.maxOrNull() ?: 0) + 1
        for (p in problems) {
            userProblems.add(p.copy(id = nextId))
            nextId++
        }
        saveUserProblems()
    }
    
    fun removeUserProblem(id: Int) {
        userProblems.removeAll { it.id == id }
        saveUserProblems()
    }
    
    private fun loadUserProblems() {
        try {
            if (userFile.exists()) {
                val json = userFile.readText()
                val type = object : TypeToken<List<Problem>>() {}.type
                val loaded: List<Problem> = gson.fromJson(json, type)
                userProblems = loaded.toMutableList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            userProblems = mutableListOf()
        }
    }
    
    private fun saveUserProblems() {
        try {
            userFile.writeText(gson.toJson(userProblems))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
