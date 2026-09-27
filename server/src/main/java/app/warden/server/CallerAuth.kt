package app.warden.server

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Authenticates callers by their APK signing certificate, not by package name.
 *
 * Package names are attacker-controllable in some shared-uid / re-sign
 * scenarios; the signing certificate is not. Every grant is bound to a
 * cert-hash, and every privileged call re-derives the caller's cert-hash and
 * matches it. The manager app is authenticated the same way against Warden's
 * own release key.
 */
class CallerAuth(private val managerCertSha256: String?) {

    data class Identity(val uid: Int, val pkg: String?, val certSha256: String?)

    fun identify(uid: Int): Identity {
        val pkg = Hidden.packagesForUid(uid)?.firstOrNull()
        val cert = pkg?.let { certHash(it, uid / 100_000) }
        return Identity(uid, pkg, cert)
    }

    /** True if the caller is Warden's own manager (matched by signing key). */
    fun isManager(uid: Int): Boolean {
        if (managerCertSha256 == null) return false
        val id = identify(uid)
        return id.pkg == BinderPublisher.MANAGER_PACKAGE &&
            id.certSha256 != null &&
            id.certSha256.equals(managerCertSha256, ignoreCase = true)
    }

    private fun certHash(pkg: String, userId: Int): String? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= 28)
            PackageManager.GET_SIGNING_CERTIFICATES.toLong()
        else
            @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES.toLong()
        val info: PackageInfo = Hidden.getPackageInfo(pkg, flags, userId) ?: return null
        val sig = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION") info.signatures?.firstOrNull()?.toByteArray()
        } ?: return null
        MessageDigest.getInstance("SHA-256").digest(sig)
            .joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
