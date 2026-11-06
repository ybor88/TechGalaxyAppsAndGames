// Copyright © Roberto Di Flumeri
package com.scouttable.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.scouttable.app.MainActivity
import com.scouttable.app.R
import com.scouttable.app.data.ReviewPrefs
import java.time.LocalDate
import java.time.MonthDay

/**
 * Avviso di fine anno sportivo, uno per sport all'anno: ricorda di aggiornare Bacheca (vetrine dei
 * premi), Detentori e Game con la stagione appena conclusa. Controllato ogni giorno da
 * [MonthlyReviewWorker]; l'anno dell'ultimo avviso è salvato in [ReviewPrefs] (e quindi nel backup).
 */
object SeasonEndNotifier {

    /** Fine stagione per sport: calcio dopo le finali di coppa di fine maggio/inizio giugno, basket
     *  dopo le Finals NBA di metà giugno. */
    private enum class SeasonEnd(val label: String, val day: MonthDay, val notificationId: Int) {
        CALCIO("Calcio", MonthDay.of(6, 10), 2000),
        BASKET("Basket", MonthDay.of(7, 1), 2001),
    }

    private const val CHANNEL_ID = "season_end"
    /** Oltre questi giorni dalla fine stagione non si avvisa più (es. app installata a novembre). */
    private const val WINDOW_DAYS = 30L

    suspend fun checkAndNotify(context: Context, prefs: ReviewPrefs, today: LocalDate = LocalDate.now()) {
        SeasonEnd.entries.forEach { season ->
            val end = season.day.atYear(today.year)
            val due = !today.isBefore(end) && today.isBefore(end.plusDays(WINDOW_DAYS))
            if (due && prefs.seasonEndNotifiedYear(season.name) < today.year && notify(context, season, today.year)) {
                prefs.markSeasonEndNotified(season.name, today.year)
            }
        }
    }

    /** true se la notifica è stata mostrata (false senza permesso: si riprova il giorno dopo). */
    private fun notify(context: Context, season: SeasonEnd, year: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false

        val channel = NotificationChannel(CHANNEL_ID, "Fine stagione", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Promemoria di fine anno sportivo per aggiornare Bacheca, Detentori e Game" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val title = "${season.label}: stagione ${year - 1}-${year % 100} terminata"
        val text = "Aggiorna Bacheca, Detentori e Game con i risultati della stagione appena conclusa."
        val openApp = PendingIntent.getActivity(
            context,
            season.notificationId,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(season.notificationId, notification)
        return true
    }
}
