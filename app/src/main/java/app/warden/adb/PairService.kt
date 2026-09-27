package app.warden.adb

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Runs pairing + broker launch in a foreground service. A BroadcastReceiver's
 * goAsync window is ~10s, but discover + pair + connect can exceed that and the
 * receiver's process gets reclaimed mid-pair — which is why the first cut showed
 * "not running" after a correct code. A foreground service is not time-limited.
 */
class PairService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val code = intent?.getStringExtra(EXTRA_CODE).orEmpty()
        PairNotification.ensureChannel(this)
        startForeground(PairNotification.NOTIF_ID, buildOngoing("Pairing…"))

        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching { AdbStarter.pairAndStart(applicationContext, code) }
                .getOrElse { Result.failure(it) }
            val ok = result.getOrNull() is AdbStarter.Outcome.Launched
            val msg = result.fold(
                onSuccess = {
                    when (it) {
                        is AdbStarter.Outcome.Launched -> "Paired — server started ✓"
                        AdbStarter.Outcome.PairNeeded -> "Paired, but connect failed. Tap Start again."
                    }
                },
                onFailure = { it.message ?: "Pairing failed." },
            )
            Log.i("Warden", "pair result: ok=$ok msg=$msg")
            if (ok) {
                PairNotification.clear(this@PairService)
            } else {
                PairNotification.result(this@PairService, msg, ongoing = true)
                PairNotification.prompt(this@PairService)   // retry with a fresh code
            }
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun buildOngoing(text: String) =
        androidx.core.app.NotificationCompat.Builder(this, PairNotification.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Warden").setContentText(text)
            .setOngoing(true).build()

    companion object {
        const val EXTRA_CODE = "code"
        fun start(ctx: Context, code: String) {
            val i = Intent(ctx, PairService::class.java).putExtra(EXTRA_CODE, code)
            androidx.core.content.ContextCompat.startForegroundService(ctx, i)
        }
    }
}
