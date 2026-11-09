package com.matchmind.app.avvisi

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.matchmind.app.MatchMindApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Segue le partite delle schedine in corso anche ad app chiusa e a schermo spento (le partite di notte):
 *  - mentre una partita è in corso controlla il punteggio ogni [INTERVALLO_LIVE] e, se il pronostico sta
 *    perdendo, manda la notifica "Fai cashout!" con il beep;
 *  - tra una partita e l'altra non consuma: si addormenta e si fa svegliare da una sveglia all'inizio
 *    della prossima partita;
 *  - si ferma da solo quando tutte le schedine sono decise.
 *
 * © Roberto Di Flumeri
 */
class MonitoraggioService : Service() {

    companion object {
        const val INTERVALLO_LIVE = 2 * 60 * 1000L
        /** Partite iniziate da molto e non ancora decise (rinvii, supplementari): controllo lento. */
        const val INTERVALLO_LENTO = 30 * 60 * 1000L
        /** Oltre questo tempo dall'inizio una partita non è più "in diretta". */
        const val DURATA_LIVE = 4 * 3600 * 1000L

        /** Avvia il monitoraggio (se è già attivo lo sveglia). */
        fun avvia(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, MonitoraggioService::class.java)) }
        }

        /** Avvia il monitoraggio solo se gli avvisi sono attivi e c'è una partita da seguire. */
        suspend fun avviaSeServe(context: Context) {
            val app = context.applicationContext as MatchMindApp
            if (!app.impostazioni.preferenze.first().avvisiCashout) return
            if (app.repository.statoMonitoraggio().niente) return
            avvia(context)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ciclo: Job? = null
    private val sveglia = Channel<Unit>(Channel.CONFLATED)
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var ultimoAvvio = 0
    private val ora = DateTimeFormatter.ofPattern("HH:mm")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifiche.creaCanali(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ultimoAvvio = startId
        // la sveglia ha appena svegliato il telefono: lo si tiene sveglio finché il ciclo non riparte
        tieniSveglio(60_000)
        try {
            ServiceCompat.startForeground(this, Notifiche.ID_MONITORAGGIO, Notifiche.monitoraggio(this, "Controllo le partite…"),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        } catch (e: Exception) {
            // avvio in primo piano non consentito in questo momento: si riproverà alla prossima apertura dell'app
            rilascia()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (ciclo?.isActive == true) sveglia.trySend(Unit) else ciclo = scope.launch { esegui() }
        return START_STICKY
    }

    private suspend fun esegui() {
        while (true) {
            val attesa = try {
                passo() ?: break
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                INTERVALLO_LIVE // errore imprevisto (archivio, rete): si riprova tra poco
            }
            withTimeoutOrNull(attesa) { sveglia.receive() }
        }
        withContext(Dispatchers.Main) { termina() }
    }

    /** Un controllo: restituisce quanto aspettare prima del prossimo, o null se non c'è più niente da seguire. */
    private suspend fun passo(): Long? {
        val app = application as MatchMindApp
        if (!app.impostazioni.preferenze.first().avvisiCashout) return null
        val prima = app.repository.statoMonitoraggio()
        if (prima.niente) return null
        if (prima.iniziate.isNotEmpty()) {
            if (prima.iniziate.any { System.currentTimeMillis() - it < DURATA_LIVE }) tieniSveglio(INTERVALLO_LIVE + 60_000)
            val esito = app.repository.verifica(conOsservazioni = false)
            Notifiche.cashout(this, esito.allerte)
        }

        val adesso = System.currentTimeMillis()
        val stato = app.repository.statoMonitoraggio(adesso)
        if (stato.niente) return null
        val live = stato.iniziate.count { adesso - it < DURATA_LIVE }
        val attesa = if (live > 0) INTERVALLO_LIVE else {
            rilascia()
            // 30 secondi dopo il fischio d'inizio, oppure il controllo lento delle partite in sospeso
            val finoAlProssimo = stato.prossimoInizio?.let { it - adesso + 30_000 } ?: Long.MAX_VALUE
            minOf(finoAlProssimo, if (stato.iniziate.isNotEmpty()) INTERVALLO_LENTO else Long.MAX_VALUE)
        }
        programmaSveglia(adesso + attesa)
        aggiornaNotifica(when {
            live > 0 -> if (live == 1) "1 partita in corso: ti avviso se la stai perdendo" else "$live partite in corso: ti avviso se le stai perdendo"
            stato.prossimoInizio != null -> "Prossima partita alle ${Instant.ofEpochMilli(stato.prossimoInizio).atZone(ZoneId.systemDefault()).format(ora)}"
            else -> "In attesa dei risultati finali"
        })
        return attesa
    }

    /** Fine del ciclo: se nel frattempo è arrivato un nuovo avvio (schedina appena giocata) si ricomincia. */
    private fun termina() {
        annullaSveglia()
        rilascia()
        if (stopSelfResult(ultimoAvvio)) ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        else ciclo = scope.launch { esegui() }
    }

    private fun aggiornaNotifica(testo: String) {
        getSystemService(NotificationManager::class.java).notify(Notifiche.ID_MONITORAGGIO, Notifiche.monitoraggio(this, testo))
    }

    // ── Telefono sveglio durante le partite in corso ──────────────────────────

    @Synchronized
    private fun tieniSveglio(durata: Long) {
        val wl = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MatchMind:monitoraggio").apply { setReferenceCounted(false) }
            .also { wakeLock = it }
        wl.acquire(durata)
    }

    @Synchronized
    private fun rilascia() {
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    // ── Sveglia: riattiva il servizio all'ora giusta anche se il telefono dorme ─

    private fun intentSveglia(): PendingIntent = PendingIntent.getForegroundService(
        this, 0, Intent(this, MonitoraggioService::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun programmaSveglia(quando: Long) {
        if (quando == Long.MAX_VALUE || quando < 0) return
        val am = getSystemService(AlarmManager::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, intentSveglia())
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quando, intentSveglia())
            }
        }
    }

    private fun annullaSveglia() {
        runCatching { getSystemService(AlarmManager::class.java).cancel(intentSveglia()) }
    }

    override fun onDestroy() {
        scope.cancel()
        rilascia()
        super.onDestroy()
    }
}
