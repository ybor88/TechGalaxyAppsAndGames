package com.example.playerbase.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Persistenza permanente (SharedPreferences) dei colori del brand scelti
 * dall'utente nel pannello "Personalizza colori", così restano impostati
 * anche dopo aver chiuso e riaperto l'app.
 */
object ThemeColorStore {
    private const val PREFS_NAME = "theme_colors"

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        BrandColors.navy = prefs.readColor("navy", BrandColors.navy)
        BrandColors.gold = prefs.readColor("gold", BrandColors.gold)
        BrandColors.basket = prefs.readColor("basket", BrandColors.basket)
        BrandColors.calcio = prefs.readColor("calcio", BrandColors.calcio)
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt("navy", BrandColors.navy.toArgb())
            .putInt("gold", BrandColors.gold.toArgb())
            .putInt("basket", BrandColors.basket.toArgb())
            .putInt("calcio", BrandColors.calcio.toArgb())
            .apply()
    }

    private fun SharedPreferences.readColor(key: String, fallback: Color): Color =
        if (contains(key)) Color(getInt(key, 0)) else fallback
}
