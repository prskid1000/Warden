package app.warden.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.IBinder

/**
 * Two jobs:
 *  1. Receives the broker binder from the server on the ADB-start path (the
 *     server's ManagerHandshake calls "deliverBinder" here) and hands it to
 *     WardenClient.
 *  2. Relays that binder to granted client apps that call "getBinder" — so a
 *     third-party app links :api and receives the broker without touching ADB.
 *     The broker enforces the caller's grant on every call regardless.
 */
class WardenProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        WardenClient.connect()   // root-start path: pick up ServiceManager "warden"
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = when (method) {
        "deliverBinder" -> {
            extras?.getBinder("binder")?.let { delivered = it; WardenClient.attach(it) }
            Bundle()
        }
        "getBinder" -> Bundle().apply { putBinder("binder", delivered) }
        else -> null
    }

    override fun query(u: Uri, p: Array<String>?, s: String?, sa: Array<String>?, o: String?): Cursor? = null
    override fun getType(u: Uri): String? = null
    override fun insert(u: Uri, v: ContentValues?): Uri? = null
    override fun delete(u: Uri, s: String?, sa: Array<String>?) = 0
    override fun update(u: Uri, v: ContentValues?, s: String?, sa: Array<String>?) = 0

    companion object { @Volatile private var delivered: IBinder? = null }
}
