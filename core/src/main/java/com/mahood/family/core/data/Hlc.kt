package com.mahood.family.core.data

/**
 * Hybrid logical clock. Timestamps are strings that sort correctly as text:
 * 13-digit wall millis, 5-digit counter, then the device id as a tie-breaker.
 * Every change gets a timestamp; when two phones change the same field,
 * the larger timestamp wins on every phone (last-writer-wins CRDT).
 */
class Hlc(private val deviceId: String, private val now: () -> Long = System::currentTimeMillis) {
    private var lastMillis = 0L
    private var counter = 0

    @Synchronized
    fun next(): String {
        val wall = now()
        if (wall > lastMillis) {
            lastMillis = wall
            counter = 0
        } else {
            counter++
        }
        return format(lastMillis, counter, deviceId)
    }

    /** Move our clock forward after seeing a remote timestamp, so our next change sorts after it. */
    @Synchronized
    fun observe(remote: String) {
        val (millis, count) = parse(remote) ?: return
        if (millis > lastMillis || (millis == lastMillis && count > counter)) {
            lastMillis = millis
            counter = count
        }
    }

    companion object {
        fun format(millis: Long, counter: Int, deviceId: String): String =
            String.format(java.util.Locale.ROOT, "%013d-%05d-%s", millis, counter, deviceId)

        fun parse(ts: String): Pair<Long, Int>? {
            val parts = ts.split("-", limit = 3)
            if (parts.size < 3) return null
            val millis = parts[0].toLongOrNull() ?: return null
            val count = parts[1].toIntOrNull() ?: return null
            return millis to count
        }

        fun millisOf(ts: String): Long = parse(ts)?.first ?: 0L
    }
}
