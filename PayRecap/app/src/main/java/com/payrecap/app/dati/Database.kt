// PayRecap — © Roberto Di Flumeri
package com.payrecap.app.dati

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import com.payrecap.app.dominio.Cedolino
import kotlinx.coroutines.flow.Flow

@Dao
interface CedolinoDao {
    @Query("SELECT * FROM cedolini ORDER BY anno DESC, mese DESC, aggiuntiva DESC")
    fun tutti(): Flow<List<Cedolino>>

    @Query("SELECT * FROM cedolini WHERE anno = :anno AND mese = :mese AND aggiuntiva = :aggiuntiva LIMIT 1")
    suspend fun perPeriodo(anno: Int, mese: Int, aggiuntiva: Boolean): Cedolino?

    @Upsert
    suspend fun salva(c: Cedolino): Long

    @Delete
    suspend fun elimina(c: Cedolino)

    @Query("DELETE FROM cedolini")
    suspend fun eliminaTutti()
}

@Database(entities = [Cedolino::class], version = 1, exportSchema = false)
abstract class PayRecapDb : RoomDatabase() {
    abstract fun cedolini(): CedolinoDao

    companion object {
        @Volatile private var istanza: PayRecapDb? = null

        fun get(context: Context): PayRecapDb = istanza ?: synchronized(this) {
            istanza ?: Room.databaseBuilder(context.applicationContext, PayRecapDb::class.java, "payrecap.db")
                .fallbackToDestructiveMigration()
                .build().also { istanza = it }
        }
    }
}
