package app.warden.server

import java.lang.reflect.Modifier

/**
 * Resolves a (binder-interface descriptor, transaction code) pair to a method
 * name, so the audit log reads `IPackageManager#installPackage` instead of the
 * opaque `IPackageManager#txn:11`.
 *
 * Framework AIDL stubs expose `static final int TRANSACTION_<method>` fields on
 * their `$Stub` class. We reflect those once per descriptor and invert the map.
 */
object TransactionNames {
    private const val PREFIX = "TRANSACTION_"
    // Read and filled from many binder threads at once.
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Map<Int, String>>()

    fun resolve(descriptor: String, code: Int): String {
        val table = cache.getOrPut(descriptor) { build(descriptor) }
        return table[code] ?: "txn:$code"
    }

    private fun build(descriptor: String): Map<Int, String> = runCatching {
        val stub = Class.forName("$descriptor\$Stub")
        stub.declaredFields
            .filter {
                Modifier.isStatic(it.modifiers) && it.name.startsWith(PREFIX) &&
                    it.type == Int::class.javaPrimitiveType
            }
            .associate { f ->
                f.isAccessible = true
                (f.getInt(null)) to f.name.removePrefix(PREFIX)
            }
    }.getOrDefault(emptyMap())
}
