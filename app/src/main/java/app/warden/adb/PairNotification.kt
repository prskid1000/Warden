package app.warden.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

/**
 * The pairing code changes the moment you leave the pairing dialog, so the user
 * must never leave it. This posts a notification with an inline reply field:
 * the user opens the pairing dialog, pulls down the shade (the dialog stays open
 * behind it, so its code and mDNS pairing service stay alive), and types the code
 * straight into the notification — no app switch, no stale code.
 */
object PairNotification {
    const val CHANNEL = "warden_pairing"
    const val NOTIF_ID = 4201
    const val ACTION_REPLY = "app.warden.action.PAIR_REPLY"
    const val KEY_CODE = "code"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Pairing", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Enter the ADB pairing code here" }
            )
        }
    }

    fun prompt(ctx: Context) {
        ensureChannel(ctx)
        val remoteInput = RemoteInput.Builder(KEY_CODE)
            .setLabel("6-digit code")
            .build()
        val replyIntent = Intent(ctx, PairReplyReceiver::class.java).setAction(ACTION_REPLY)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pi = PendingIntent.getBroadcast(ctx, 0, replyIntent, flags)
        val action = NotificationCompat.Action.Builder(0, "Enter code & pair", pi)
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(false)
            .build()

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Pair Warden")
            .setContentText("Open the pairing screen, then type the 6-digit code here.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "In Wireless debugging → \"Pair device with pairing code\", keep that " +
                    "screen open, pull this shade down and type the 6-digit code here."))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(action)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    fun result(ctx: Context, text: String, ongoing: Boolean = false) {
        ensureChannel(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Warden")
            .setContentText(text)
            .setOngoing(ongoing)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    fun clear(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }
}
