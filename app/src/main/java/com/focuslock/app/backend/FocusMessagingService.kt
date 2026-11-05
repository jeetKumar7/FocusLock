package com.focuslock.app.backend

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.focuslock.app.MainActivity
import com.focuslock.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FocusMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch { Backend.registerToken() }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val n = message.notification
        val title = n?.title ?: "FocusLock"
        val body = n?.body ?: message.data["body"] ?: "An update from your partner."
        notify(title, body)
    }

    private fun notify(title: String, body: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL, "Partner alerts", NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0, android.content.Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        mgr.notify((System.currentTimeMillis() % 100000).toInt(), notif)
    }

    companion object {
        private const val CHANNEL = "partner_alerts"
    }
}
