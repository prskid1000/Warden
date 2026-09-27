package app.warden.server

import android.os.IBinder

/** Thin wrapper over the hidden ServiceManager.addService (root start only). */
object ServiceManagerCompat {
    fun addService(name: String, binder: IBinder) = Hidden.addService(name, binder)
}
