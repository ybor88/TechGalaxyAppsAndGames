package com.matchmind.app.avvisi

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.matchmind.app.MainActivity
import com.matchmind.app.R
import com.matchmind.app.dati.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Notifiche di MatchMind:
 *  - "Fai cashout!": una partita in corso sta facendo perdere una schedina ancora aperta (con beep);
 *  - monitoraggio: la notifica fissa del servizio che segue le partite anche ad app chiusa.
 *
 * Il beep suona sul canale della sveglia, così si sente anche con la suoneria in silenzioso (di notte).
 *
 * © Roberto Di Flumeri
 */
object Notifiche {
    const val CANALE_CASHOUT = "cashout"
    const val CANALE_MONITORAGGIO = "monitoraggio"
    const val ID_MONITORAGGIO = 1

    fun creaCanali(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANALE_CASHOUT, "Avvisi cashout", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avvisa con un beep quando una partita in corso sta facendo perdere la schedina"
            // il suono è il beep sul canale sveglia (vedi beep()): il canale resta muto per non suonare due volte
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 250, 600, 250, 600)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel(CANALE_MONITORAGGIO, "Monitoraggio partite", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Notifica fissa mentre MatchMind segue le partite delle tue schedine"
            setShowBadge(false)
        })
    }

    private fun apriApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun monitoraggio(context: Context, testo: String) = NotificationCompat.Builder(context, CANALE_MONITORAGGIO)
        .setSmallIcon(R.drawable.ic_notifica)
        .setContentTitle("MatchMind segue le tue partite")
        .setContentText(testo)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setContentIntent(apriApp(context))
        .build()

    fun testoAllerta(a: Repository.Allerta): String =
        (if (a.giaPersa) "Pronostico ormai perso" else "La stai perdendo") +
            ": ${a.casa} ${a.risultato} ${a.ospite} · hai giocato ${a.scelta.simbolo}"

    /** Una notifica per ogni partita che sta perdendo, poi il beep. */
    suspend fun cashout(context: Context, allerte: List<Repository.Allerta>) {
        if (allerte.isEmpty()) return
        val permesso = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (permesso) {
            val nm = NotificationManagerCompat.from(context)
            for (a in allerte) {
                val testo = testoAllerta(a)
                val n = NotificationCompat.Builder(context, CANALE_CASHOUT)
                    .setSmallIcon(R.drawable.ic_notifica)
                    .setContentTitle("⚠️ FAI CASHOUT!")
                    .setContentText(testo)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("$testo.\nSe vuoi salvare la bolletta, fai cashout adesso."))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setColor(0xFFE5484D.toInt())
                    .setAutoCancel(true)
                    .setContentIntent(apriApp(context))
                    .build()
                try {
                    nm.notify(ID_MONITORAGGIO + 1 + (a.selezioneId % 100_000).toInt(), n)
                } catch (_: SecurityException) { }
            }
        }
        beep()
    }

    /** Tre beep sul canale della sveglia (si sentono anche con il telefono in silenzioso). */
    suspend fun beep() = withContext(Dispatchers.Default) {
        val toni = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME) }.getOrNull() ?: return@withContext
        try {
            repeat(3) {
                toni.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500)
                delay(800)
            }
        } finally {
            toni.release()
        }
    }
}
