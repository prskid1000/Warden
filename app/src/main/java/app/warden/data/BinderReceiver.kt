package app.warden.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.warden.api.BinderContainer
import app.warden.api.IWarden
import app.warden.api.WardenContract

/**
 * Receives the broker binder from the shell-started server (ADB path).
 *
 * The server broadcasts ACTION_BINDER with a BinderContainer extra. We validate
 * the delivered binder by actually calling it (apiVersion/serverUid) before
 * trusting it, so a spoofed broadcast from another app is rejected.
 */
class BinderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WardenContract.ACTION_BINDER) return
        val container = intent.getParcelableExtra(
            WardenContract.EXTRA_BINDER, BinderContainer::class.java
        ) ?: run {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(WardenContract.EXTRA_BINDER)
        }
        val binder = (container as? BinderContainer)?.binder
        if (binder == null || !binder.isBinderAlive) {
            Log.w("Warden", "binder broadcast with no live binder"); return
        }
        val svc = IWarden.Stub.asInterface(binder)
        val ok = runCatching { svc.apiVersion() >= 1 && svc.serverUid() >= 0 }.getOrDefault(false)
        if (!ok) { Log.w("Warden", "rejected unverifiable binder"); return }
        WardenClient.attach(binder)
        Log.i("Warden", "broker binder received via ADB handshake (uid=${runCatching { svc.serverUid() }.getOrDefault(-1)})")
    }
}
