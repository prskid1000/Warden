package app.warden.server

/**
 * A scoped, optionally-expiring permission bound to a caller's signing cert.
 *
 * [scopes] gates what the app may reach:
 *   "*"                     — everything (discouraged; the UI warns)
 *   "exec"                  — Warden.newProcess / su shim
 *   "svc:<descriptor>"      — wrap that system service, e.g. "svc:android.content.pm.IPackageManager"
 * [expiresAt] == 0 means permanent; otherwise epoch millis after which it lapses.
 */
data class Grant(
    val pkg: String,
    val certSha256: String?,
    val scopes: Set<String>,
    val expiresAt: Long = 0L,
) {
    fun isLive(now: Long = System.currentTimeMillis()): Boolean =
        expiresAt == 0L || now < expiresAt

    fun covers(scope: String): Boolean =
        "*" in scopes || scope in scopes ||
            // "svc:*" style prefix wildcards
            scopes.any { it.endsWith("*") && scope.startsWith(it.dropLast(1)) }

    companion object {
        const val SCOPE_ALL = "*"
        const val SCOPE_EXEC = "exec"
        fun service(descriptor: String) = "svc:$descriptor"
    }
}
