package com.scouttable.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// NB: incrementare questo numero ogni volta che cambiano i campi di Player — altrimenti Room
// rifiuta di aprire il database su un'installazione esistente (identity hash mismatch), anche
// con fallbackToDestructiveMigration() attivo (che scatta solo su un vero cambio di versione).
@Database(entities = [Player::class], version = 3, exportSchema = false)
@TypeConverters(SportConverter::class)
abstract class ScoutTableDatabase : RoomDatabase() {
    abstract fun playerDao(): PlayerDao

    companion object {
        const val DB_NAME = "scouttable.db"

        @Volatile
        private var instance: ScoutTableDatabase? = null

        fun get(context: Context): ScoutTableDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScoutTableDatabase::class.java,
                    DB_NAME,
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
