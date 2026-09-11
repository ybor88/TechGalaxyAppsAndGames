package com.scouttable.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// NB: incrementare questo numero ogni volta che cambiano i campi di Player — e aggiungere qui
// sotto la migrazione corrispondente (vedi MIGRATION_3_4/MIGRATION_4_5): senza una migrazione
// esplicita, fallbackToDestructiveMigration() cancella TUTTI i dati degli utenti già installati
// ad ogni aggiornamento che cambia lo schema (successo per errore due volte in questa sessione,
// aggiungendo "gol subiti" e poi le statistiche in Nazionale) — non accettabile per un semplice
// aggiornamento dell'app. fallbackToDestructiveMigration() resta solo come rete di sicurezza per
// salti di versione non altrimenti previsti (es. da un'installazione molto più vecchia).
private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE players ADD COLUMN golSubiti INTEGER NOT NULL DEFAULT 0")
    }
}
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE players ADD COLUMN presenzeNazionale INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN punteggioNazionale INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN assistNazionale INTEGER NOT NULL DEFAULT 0")
    }
}
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE players ADD COLUMN rimbalzi INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN palleRecuperate INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN tiriDaDue INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN tiriDaTre INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN rimbalziNazionale INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN palleRecuperateNazionale INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN tackle INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN golEvitati INTEGER NOT NULL DEFAULT 0")
    }
}
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE players ADD COLUMN golSubitiNazionale INTEGER NOT NULL DEFAULT 0")
    }
}
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE players ADD COLUMN percentualeTiriDaDue INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE players ADD COLUMN percentualeTiriDaTre INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(entities = [Player::class], version = 8, exportSchema = false)
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
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }

        /**
         * Chiude la connessione corrente e scarta l'istanza: necessario prima di leggere/sovrascrivere
         * il file del database da fuori Room (export/import backup), altrimenti una connessione
         * già aperta continuerebbe a usare pagine/cache riferite al file precedente.
         */
        fun closeAndReset() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
