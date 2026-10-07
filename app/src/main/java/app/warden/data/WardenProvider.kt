package app.warden.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle

/**
 * Relays the broker binder to granted client apps that call "getBinder" — so a
 * third-party app links :api and receives the broker without touching ADB. The
 * broker enforces the caller's grant on every call regardless.
 *
 * While the server runs it holds an external handle on this provider, which
 * keeps this process alive and restarts it if it dies, delivering the binder each
 * time (see the server's ManagerTether). A client can still arrive in the moment
 * between a restart and that delivery, so "getBinder" waits for it rather than
 * returning empty.
 */
class WardenProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        WardenClient.connect()   // root-start path: pick up ServiceManager "warden"
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = when (method) {
        // Runs on a binder thread, so waiting here doesn't block the broadcast
        // that delivers the binder (main thread).
        "getBinder" -> Bundle().apply {
            putBinder("binder", WardenClient.awaitBinder(HANDSHAKE_WAIT_MS))
        }
        else -> null
    }

    override fun query(u: Uri, p: Array<String>?, s: String?, sa: Array<String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, sa: Array<String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, sa: Array<String>?) = 0

    companion object {
        // The handshake normally lands in tens of ms; this bound is only reached
        // when the server isn't running, and is how long a client waits to learn that.
        private const val HANDSHAKE_WAIT_MS = 2000L
    }
}
