package com.wangyu.gotsumego.data

import com.google.gson.annotations.SerializedName

/**
 * JSON格式的题目数据模型
 * 对应 problems_full.json 的结构
 */
data class JsonProblem(
    @SerializedName("id")
    val id: Int,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("difficulty")
    val difficulty: Int,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("boardSize")
    val boardSize: Int,
    
    @SerializedName("stones")
    val stones: List<List<Int>>,
    
    @SerializedName("toPlay")
    val toPlay: Int,
    
    @SerializedName("answer")
    val answer: List<Int>,
    
    @SerializedName("solutions")
    val solutions: List<JsonSolution>?,
    
    @SerializedName("hint")
    val hint: String?
)

data class JsonSolution(
    @SerializedName("moves")
    val moves: List<List<Int>>,
    
    @SerializedName("result")
    val result: String,
    
    @SerializedName("comment")
    val comment: String?
)
