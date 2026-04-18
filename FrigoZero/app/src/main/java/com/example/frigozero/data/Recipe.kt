package com.example.frigozero.data

data class Recipe(
    val id: Int,
    val name: String,
    val description: String,
    val ingredients: List<String>,
    val steps: List<String>,
    val emoji: String,
    val cookTimeMinutes: Int,
    val difficulty: String  // "Easy", "Medium", "Hard"
)

