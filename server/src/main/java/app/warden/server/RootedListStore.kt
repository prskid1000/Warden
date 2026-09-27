package app.warden.server

import app.warden.api.RootedEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The rooted list. Persisted so the Zygisk module (layer C) can read it at
 * process-fork time, and so the broker knows which apps get su/broker access.
 *
 * The module reads [moduleView] (a flat, fast-to-parse file) on each specialize;
 * the manager writes rich entries here.
 */
class RootedListStore(private val dir: File) {
    private val file = File(dir, "rooted.json").apply { parentFile?.mkdirs() }
    // Consumed by the native Zygisk module: one "pkg profile" line per spoofed app.
    private val moduleView = File(dir, "rooted.list").apply { parentFile?.mkdirs() }
    private val entries = LinkedHashMap<String, RootedEntry>()

    init { load() }

    @Synchronized
    fun all(): List<RootedEntry> = entries.values.toList()

    @Synchronized
    fun get(pkg: String): RootedEntry? = entries[pkg]

    @Synchronized
    fun set(e: RootedEntry) {
        entries[e.pkg] = e
        persist()
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val e = RootedEntry(
                    pkg = o.getString("pkg"),
                    giveBroker = o.optBoolean("broker"),
                    giveSu = o.optBoolean("su"),
                    spoofDetection = o.optBoolean("spoof"),
                    propsProfile = o.optString("props"),
                )
                entries[e.pkg] = e
            }
        }
    }

    private fun persist() {
        val arr = JSONArray()
        entries.values.forEach { e ->
            arr.put(JSONObject().apply {
                put("pkg", e.pkg); put("broker", e.giveBroker); put("su", e.giveSu)
                put("spoof", e.spoofDetection); put("props", e.propsProfile)
            })
        }
        runCatching { file.writeText(arr.toString()) }
        // Native module view: only packages that want detection spoofing.
        val sb = StringBuilder()
        entries.values.filter { it.spoofDetection }.forEach {
            sb.append(it.pkg).append(' ')
              .append(it.propsProfile.ifEmpty { "rooted" }).append('\n')
        }
        runCatching { moduleView.writeText(sb.toString()) }
    }
}
