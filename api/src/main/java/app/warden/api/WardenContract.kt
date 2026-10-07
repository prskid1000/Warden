package app.warden.api

/** Constants shared by the server (sender) and manager app (receiver). */
object WardenContract {
    const val MANAGER_PACKAGE = "app.warden"
    const val ACTION_BINDER = "app.warden.action.BINDER"
    const val EXTRA_BINDER = "app.warden.extra.BINDER"
    // The manager's provider; relays the broker binder to granted apps.
    const val AUTHORITY = "app.warden.broker"
    const val PROVIDER_URI = "content://$AUTHORITY"
}
