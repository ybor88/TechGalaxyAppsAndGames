package com.example.turbobrothers.data

import android.content.Context
import android.content.SharedPreferences

object HighScoreStore {
    private const val PREFS_NAME = "turbo_brothers_prefs"
    private const val KEY_HIGH_SCORE = "high_score"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getHighScore(): Int = prefs.getInt(KEY_HIGH_SCORE, 0)

    fun saveIfHigher(score: Int): Boolean {
        val current = getHighScore()
        if (score > current) {
            prefs.edit().putInt(KEY_HIGH_SCORE, score).apply()
            return true
        }
        return false
    }

    /** Svuota il record salvato: dal prossimo avvio il gioco riparte da zero. */
    fun resetHighScore() {
        prefs.edit().putInt(KEY_HIGH_SCORE, 0).apply()
    }
}
