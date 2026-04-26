package com.wangyu.gotsumego.data

import com.google.gson.annotations.SerializedName

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
    
    @SerializedName("book")
    val book: String?,
    
    @SerializedName("solutionMoves")
    val solutionMoves: List<List<Int>>?,
    
    @SerializedName("solutionComment")
    val solutionComment: String?,
    
    @SerializedName("hint")
    val hint: String?
)
