package com.scouttable.app.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Nessun framework DI nel workspace: un semplice provider manuale, coerente con lo stile delle altre app. */
fun buildRepository(context: Context): PlayerRepository {
    val dao = ScoutTableDatabase.get(context).playerDao()
    val prefs = ReviewPrefs(context)
    return PlayerRepository(dao, prefs)
}

@Composable
fun rememberPlayerRepository(): PlayerRepository {
    val context = LocalContext.current.applicationContext
    return remember { buildRepository(context) }
}
