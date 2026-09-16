package it.meteomottavisconti

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MeteoFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "meteo_alerts"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val authToken = AccountManager.getToken(this) ?: return
        ApiClient.registerDevice(authToken, token) { /* si ritenta al prossimo avvio se fallisce */ }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val downloadUrl = message.data["download_url"]
        showNotification(title, body, downloadUrl)
    }

    private fun showNotification(title: String, body: String, downloadUrl: String? = null) {
        createChannelIfNeeded()
        // Se il messaggio porta un link di download diretto (es. nuova versione app),
        // il tocco sulla notifica avvia subito il download invece di aprire l'app.
        val intent = if (!downloadUrl.isNullOrEmpty()) {
            Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
        } else {
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = getString(R.string.notif_channel_description)
                manager.createNotificationChannel(channel)
            }
        }
    }
}
