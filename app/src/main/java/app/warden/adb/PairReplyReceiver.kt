package app.warden.adb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import kotlinx.coroutines.runBlocking

/**
 * Receives the 6-digit code typed into the pairing notification. Because the
 * shade is open over the still-visible pairing dialog, the pairing service is
 * alive here — so we discover its port over mDNS and pair immediately, then
 * launch the broker.
 */
class PairReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PairNotification.ACTION_REPLY) return
        val code = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(PairNotification.KEY_CODE)?.toString()?.trim().orEmpty()
        if (code.length < 6) {
            PairNotification.result(context, "Enter the full 6-digit code.", ongoing = true)
            PairNotification.prompt(context)
            return
        }
        PairNotification.result(context, "Pairing…", ongoing = true)
        val pending = goAsync()
        Thread {
            try {
                val r = runBlocking { AdbStarter.pairAndStart(context.applicationContext, code) }
                val msg = r.fold(
                    onSuccess = {
                        when (it) {
                            is AdbStarter.Outcome.Launched -> "Paired — server started ✓"
                            AdbStarter.Outcome.PairNeeded -> "Paired, but couldn't connect. Tap Start again."
                        }
                    },
                    onFailure = { it.message ?: "Pairing failed." },
                )
                val ok = r.getOrNull() is AdbStarter.Outcome.Launched
                if (ok) PairNotification.clear(context) else {
                    PairNotification.result(context, msg, ongoing = true)
                    PairNotification.prompt(context)   // let them retry with a fresh code
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
