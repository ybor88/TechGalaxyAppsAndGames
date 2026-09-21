// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Nessun framework DI: provider manuale, coerente con lo stile delle altre app del workspace. */
fun buildSongRepository(context: Context): SongRepository {
    val dao = SoniqAiDatabase.get(context).songDao()
    return SongRepository(context.applicationContext, dao)
}

@Composable
fun rememberSongRepository(): SongRepository {
    val context = LocalContext.current.applicationContext
    return remember { buildSongRepository(context) }
}
