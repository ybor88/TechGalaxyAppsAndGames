package com.scouttable.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Player::class], version = 1, exportSchema = false)
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
