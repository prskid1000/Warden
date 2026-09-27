package app.warden.server

/**
 * Resolves caller uid -> package name via the real package manager (reached
 * through [Hidden] reflection, since the server holds shell/root). Returns null
 * for shared-uid ambiguity; callers then audit by uid only.
 */
class PackageResolver {
    fun packageForUid(uid: Int): String? = Hidden.packagesForUid(uid)?.firstOrNull()
}
