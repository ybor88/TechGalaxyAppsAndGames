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
import com.scouttable.app.data.Player
import com.scouttable.app.data.Sport

/** Notifica sul telefono l'elenco dei giocatori da revisionare (una notifica per sport). */
object ReviewNotifier {

    private const val CHANNEL_ID = "review"
    /** Righe di nomi mostrate nella notifica espansa: oltre, "…e altri N". */
    private const val MAX_LINES = 8

    fun notify(context: Context, sport: Sport, pending: List<Player>) {
        if (pending.isEmpty()) return
        // Da Android 13 serve il permesso POST_NOTIFICATIONS (chiesto in MainActivity): se
        // l'utente l'ha negato non si notifica, i giocatori restano comunque in "Revisione".
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)

        val title = "${sport.label}: ${pending.size} giocator${if (pending.size == 1) "e" else "i"} da revisionare"
        val names = pending.map { it.nome }
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
        names.take(MAX_LINES).forEach(style::addLine)
        if (names.size > MAX_LINES) style.setSummaryText("…e altri ${names.size - MAX_LINES}")

        val openApp = PendingIntent.getActivity(
            context,
            sport.ordinal,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(names.joinToString(", "))
            .setStyle(style)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Id fisso per sport: la notifica successiva sostituisce la precedente invece di accumularsi.
        NotificationManagerCompat.from(context).notify(1000 + sport.ordinal, notification)
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Revisione giocatori", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "Giocatori attivi da revisionare (ogni 30 giorni)" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
