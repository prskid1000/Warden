package app.warden.server

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Deny-by-default grant table, keyed on package + signing cert, holding scopes
 * and an optional expiry. Replaces the old boolean PermissionStore.
 *
 * A grant is honoured only if: it exists, is live (not expired), the caller's
 * current cert-hash matches the stored one, and the requested scope is covered.
 * A cert mismatch (app re-signed / spoofed) is treated as no grant.
 */
class GrantStore(dir: File) {
    private val file = File(dir, "grants.json").apply { parentFile?.mkdirs() }
    private val grants = HashMap<String, Grant>()   // key = pkg

    init { load() }

    @Synchronized
    fun get(pkg: String?): Grant? = pkg?.let { grants[it] }?.takeIf { it.isLive() }

    /**
     * Authorize a specific scope for an identified caller. All four gates must
     * pass. Returns true on allow.
     */
    @Synchronized
    fun authorize(id: CallerAuth.Identity, scope: String): Boolean {
        val g = grants[id.pkg ?: return false] ?: return false
        if (!g.isLive()) return false
        // A grant with no certificate (saved before grants required one) authorizes nobody.
        if (g.certSha256 == null || !g.certSha256.equals(id.certSha256, true)) return false
        return g.covers(scope)
    }

    @Synchronized
    fun put(g: Grant) { grants[g.pkg] = g; persist() }

    @Synchronized
    fun revoke(pkg: String) { grants.remove(pkg); persist() }

    @Synchronized
    fun all(): List<Grant> = grants.values.toList()

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val scopes = o.getJSONArray("scopes").let { s ->
                    (0 until s.length()).map { s.getString(it) }.toSet()
                }
                val g = Grant(o.getString("pkg"), o.optString("cert").ifEmpty { null },
                    scopes, o.optLong("exp", 0L))
                grants[g.pkg] = g
            }
        }
    }

    private fun persist() {
        val arr = JSONArray()
        grants.values.forEach { g ->
            arr.put(JSONObject().apply {
                put("pkg", g.pkg); put("cert", g.certSha256 ?: "")
                put("scopes", JSONArray(g.scopes.toList())); put("exp", g.expiresAt)
            })
        }
        runCatching { file.writeText(arr.toString()) }
    }
}
