// Copyright (c) Roberto Di Flumeri
package com.soniqai.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Song::class], version = 1, exportSchema = false)
abstract class SoniqAiDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao

    companion object {
        const val DB_NAME = "soniqai.db"

        @Volatile
        private var instance: SoniqAiDatabase? = null

        fun get(context: Context): SoniqAiDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SoniqAiDatabase::class.java,
                    DB_NAME,
                ).fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
