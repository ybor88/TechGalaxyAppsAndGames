package com.matchmind.app.dati

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Embedded
import androidx.room.Relation
import kotlinx.coroutines.flow.Flow

/**
 * Archivio locale sul telefono: schedine, pronostici, osservazioni (memoria dell'esperienza) e movimenti
 * del credito virtuale. Nessun dato lascia il dispositivo.
 *
 * © Roberto Di Flumeri
 */
@Entity(tableName = "schedine")
data class SchedinaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sport: String,
    val strategia: String,
    val creataIl: Long,
    val puntata: Double,
    val quotaTotale: Double,
    val probabilita: Double,
    val stato: String,
    val chiusaIl: Long? = null,
    val vincita: Double = 0.0,
    val verificataIl: Long? = null,
)

@Entity(
    tableName = "selezioni",
    foreignKeys = [ForeignKey(entity = SchedinaEntity::class, parentColumns = ["id"], childColumns = ["schedinaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("schedinaId"), Index("eventoId")],
)
data class SelezioneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val schedinaId: Long,
    val eventoId: String,
    val lega: String,
    val inizio: Long,
    val casa: String,
    val ospite: String,
    val scelta: String,
    val quota: Double,
    val quotaStimata: Boolean,
    val probabilita: Double,
    val pMercato: Double?,
    val pModello: Double?,
    val esito: String,
    val risultato: String? = null,
    /** Non più usato (era per il cashout): la colonna resta per non toccare l'archivio. */
    val allerta: String? = null,
    /** Perché la squadra era affidabile al momento della scelta (es. "Inter in stagione: 10 vinte su 12"). */
    val storico: String? = null,
)

data class SchedinaCompleta(
    @Embedded val schedina: SchedinaEntity,
    @Relation(parentColumn = "id", entityColumn = "schedinaId") val selezioni: List<SelezioneEntity>,
)

/** Ogni partita analizzata: le probabilità previste e, quando arriva, il risultato reale. */
@Entity(tableName = "osservazioni", indices = [Index("sport"), Index("esito")])
data class OsservazioneEntity(
    @PrimaryKey val eventoId: String,
    val sport: String,
    val lega: String,
    val inizio: Long,
    val mercatoCasa: Double?,
    val mercatoPareggio: Double?,
    val mercatoOspite: Double?,
    val modelloCasa: Double?,
    val modelloPareggio: Double?,
    val modelloOspite: Double?,
    /** null = in attesa del risultato; "NULLA" = partita non disputata. */
    val esito: String? = null,
    /** Probabilità prevista che segnino entrambe e che ci siano almeno 3 gol (solo calcio). */
    val pGoal: Double? = null,
    val pOver: Double? = null,
    /** Gol reali, salvati alla chiusura: servono per imparare su Goal/NoGoal e Under/Over. */
    val golCasa: Int? = null,
    val golOspite: Int? = null,
    /** Squadre della partita: servono a imparare l'affidabilità di ogni squadra. */
    val casaId: String? = null,
    val ospiteId: String? = null,
)

data class ConteggioSport(val sport: String, val numero: Int)

@Entity(tableName = "movimenti", indices = [Index("data")])
data class MovimentoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val data: Long,
    val tipo: String,
    val importo: Double,
    val schedinaId: Long? = null,
)

@Dao
interface SchedineDao {
    @Insert fun inserisci(s: SchedinaEntity): Long
    @Insert fun inserisciTutte(s: List<SchedinaEntity>)
    @Insert fun inserisciSelezioni(s: List<SelezioneEntity>)
    @Update fun aggiorna(s: SchedinaEntity)
    @Update fun aggiornaSelezioni(s: List<SelezioneEntity>)

    @Transaction
    @Query("SELECT * FROM schedine ORDER BY creataIl DESC")
    fun tutte(): Flow<List<SchedinaCompleta>>

    @Transaction
    @Query("SELECT * FROM schedine WHERE stato = 'IN_CORSO'")
    fun inCorso(): List<SchedinaCompleta>

    @Transaction
    @Query("SELECT * FROM schedine WHERE id = :id")
    fun perId(id: Long): SchedinaCompleta?

    @Transaction
    @Query("SELECT * FROM schedine")
    fun elenco(): List<SchedinaCompleta>

    @Query("DELETE FROM schedine")
    fun svuota()
}

@Dao
interface OsservazioniDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) fun inserisci(o: List<OsservazioneEntity>)
    @Query("SELECT * FROM osservazioni WHERE esito IS NULL AND inizio < :prima") fun aperte(prima: Long): List<OsservazioneEntity>
    @Query("SELECT * FROM osservazioni WHERE esito IS NOT NULL AND esito != 'NULLA'") fun chiuse(): List<OsservazioneEntity>
    @Query("UPDATE osservazioni SET esito = :esito, golCasa = :golCasa, golOspite = :golOspite WHERE eventoId = :id")
    fun chiudi(id: String, esito: String, golCasa: Int?, golOspite: Int?)
    @Query("SELECT sport, COUNT(*) AS numero FROM osservazioni WHERE esito IS NULL GROUP BY sport") fun conteggioAperte(): Flow<List<ConteggioSport>>
    @Query("SELECT * FROM osservazioni") fun elenco(): List<OsservazioneEntity>
    @Query("DELETE FROM osservazioni") fun svuota()
}

@Dao
interface MovimentiDao {
    @Insert fun inserisci(m: MovimentoEntity): Long
    @Insert fun inserisciTutti(m: List<MovimentoEntity>)
    @Query("SELECT * FROM movimenti ORDER BY data, id") fun tutti(): Flow<List<MovimentoEntity>>
    @Query("SELECT * FROM movimenti ORDER BY data, id") fun elenco(): List<MovimentoEntity>
    @Query("DELETE FROM movimenti") fun svuota()
}

@Database(entities = [SchedinaEntity::class, SelezioneEntity::class, OsservazioneEntity::class, MovimentoEntity::class], version = 5, exportSchema = false)
abstract class MatchMindDb : RoomDatabase() {
    abstract fun schedine(): SchedineDao
    abstract fun osservazioni(): OsservazioniDao
    abstract fun movimenti(): MovimentiDao

    companion object {
        /** v2: Goal/NoGoal e Under/Over nelle osservazioni. I dati esistenti restano. */
        val DA_1_A_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE osservazioni ADD COLUMN pGoal REAL")
                db.execSQL("ALTER TABLE osservazioni ADD COLUMN pOver REAL")
                db.execSQL("ALTER TABLE osservazioni ADD COLUMN golCasa INTEGER")
                db.execSQL("ALTER TABLE osservazioni ADD COLUMN golOspite INTEGER")
            }
        }

        /** v3: svantaggio live di una selezione (per il cashout). I dati esistenti restano. */
        val DA_2_A_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE selezioni ADD COLUMN allerta TEXT")
            }
        }

        /** v4: squadre nelle osservazioni, per l'affidabilità delle squadre. I dati esistenti restano. */
        val DA_3_A_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE osservazioni ADD COLUMN casaId TEXT")
                db.execSQL("ALTER TABLE osservazioni ADD COLUMN ospiteId TEXT")
            }
        }

        /** v5: motivo di affidabilità di ogni pronostico giocato, per il report delle sconfitte. I dati esistenti restano. */
        val DA_4_A_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE selezioni ADD COLUMN storico TEXT")
            }
        }

        fun crea(context: Context): MatchMindDb =
            Room.databaseBuilder(context, MatchMindDb::class.java, "matchmind.db").addMigrations(DA_1_A_2, DA_2_A_3, DA_3_A_4, DA_4_A_5).build()
    }
}
