package app.warden.server

import org.json.JSONObject
import java.io.File

/**
 * Deny-by-default per-package grant table. Persisted as JSON in the server's
 * private dir. The manager writes grants; the broker reads them on every call.
 */
class PermissionStore(dir: File) {
    companion object {
        const val UNKNOWN = 0
        const val GRANTED = 1
        const val DENIED = 2
    }

    private val file = File(dir, "grants.json").apply { parentFile?.mkdirs() }
    private val state = HashMap<String, Int>()

    init { load() }

    @Synchronized
    fun state(pkg: String): Int = state[pkg] ?: UNKNOWN

    @Synchronized
    fun isGranted(pkg: String): Boolean = state[pkg] == GRANTED

    @Synchronized
    fun set(pkg: String, value: Int) {
        state[pkg] = value
        persist()
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val o = JSONObject(file.readText())
            o.keys().forEach { state[it] = o.getInt(it) }
        }
    }

    private fun persist() {
        val o = JSONObject()
        state.forEach { (k, v) -> o.put(k, v) }
        runCatching { file.writeText(o.toString()) }
    }
}
