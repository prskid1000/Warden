package app.warden.server

/**
 * Per-uid token bucket. A granted app can still be buggy or hostile; this caps
 * how fast any single caller can drive the broker. Floods are denied and the
 * denial is audited like any other.
 */
class RateLimiter(
    private val capacity: Double = 200.0,     // burst
    private val refillPerSec: Double = 100.0, // sustained rate
) {
    private class Bucket(var tokens: Double, var last: Long)
    private val buckets = HashMap<Int, Bucket>()

    @Synchronized
    fun allow(uid: Int): Boolean {
        val now = System.nanoTime()
        val b = buckets.getOrPut(uid) { Bucket(capacity, now) }
        val elapsed = (now - b.last) / 1_000_000_000.0
        b.tokens = minOf(capacity, b.tokens + elapsed * refillPerSec)
        b.last = now
        return if (b.tokens >= 1.0) { b.tokens -= 1.0; true } else false
    }

    @Synchronized
    fun forget(uid: Int) { buckets.remove(uid) }
}
