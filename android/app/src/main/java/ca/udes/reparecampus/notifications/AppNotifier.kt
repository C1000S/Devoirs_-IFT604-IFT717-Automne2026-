package ca.udes.reparecampus.notifications

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
import ca.udes.reparecampus.MainActivity
import ca.udes.reparecampus.R
import ca.udes.reparecampus.data.model.AppNotification

/** Affiche les notifications système de l'application. */
class AppNotifier(private val context: Context) {

    init {
        val channel = NotificationChannel(CHANNEL_ID, "Suivi des demandes", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Changements d'état, estimations et affectations de vos demandes de réparation."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun show(n: AppNotification) {
        if (!canNotify()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            // CLEAR_TOP + SINGLE_TOP : l'intent est livré à l'activité existante (onNewIntent),
            // y compris quand la tâche est recréée après la mort du processus.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            n.request?.let { putExtra(EXTRA_REQUEST_ID, it) }
        }
        val pending = PendingIntent.getActivity(
            context, n.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_repair)
            .setContentTitle(n.title)
            .setContentText(n.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(n.message))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(n.id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission retirée entre-temps : rien à afficher.
        }
    }

    companion object {
        const val CHANNEL_ID = "repair_updates"
        const val EXTRA_REQUEST_ID = "requestId"
    }
}
