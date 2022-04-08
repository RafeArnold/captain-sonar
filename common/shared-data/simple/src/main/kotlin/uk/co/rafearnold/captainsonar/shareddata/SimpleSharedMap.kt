package uk.co.rafearnold.captainsonar.shareddata

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.schedule
import kotlin.concurrent.withLock

/**
 * Basic implementation of [SharedMap] that implements TTL on its entry.
 */
class SimpleSharedMap<K, V>(
    private val wrappedMap: MutableMap<K, V> = ConcurrentHashMap()
) : SharedMap<K, V>, MutableMap<K, V> by wrappedMap, AbstractTtlCollection() {

    private val ttlTimers: MutableMap<K, Timer> = ConcurrentHashMap()

    private val lock: Lock = ReentrantLock()

    override fun put(key: K, value: V, ttl: Long, ttlUnit: TimeUnit): V? =
        lock.withLock {
            ttlTimers.remove(key = key)?.cancel()
            val oldValue: V? = wrappedMap.put(key = key, value = value)
            scheduleRemoval(key = key, ttl = ttl, ttlUnit = ttlUnit)
            oldValue
        }

    override fun putIfAbsent(key: K, value: V, ttl: Long, ttlUnit: TimeUnit): V? =
        lock.withLock {
            val currentValue: V? = wrappedMap.putIfAbsent(key, value)
            if (currentValue == null) {
                ttlTimers.remove(key = key)?.cancel()
                scheduleRemoval(key = key, ttl = ttl, ttlUnit = ttlUnit)
            }
            currentValue
        }

    override fun put(key: K, value: V): V? =
        put(key = key, value = value, ttl = defaultTtlMillis, ttlUnit = TimeUnit.MILLISECONDS)

    override fun putIfAbsent(key: K, value: V): V? =
        putIfAbsent(key = key, value = value, ttl = defaultTtlMillis, ttlUnit = TimeUnit.MILLISECONDS)

    override fun remove(key: K): V? =
        lock.withLock {
            ttlTimers.remove(key = key)?.cancel()
            wrappedMap.remove(key = key)
        }

    override fun remove(key: K, value: V): Boolean =
        lock.withLock {
            val removed: Boolean = wrappedMap.remove(key = key, value = value)
            if (removed) {
                ttlTimers.remove(key = key)?.cancel()
            }
            removed
        }

    override fun putAll(from: Map<out K, V>) {
        for ((key, value) in from) put(key = key, value = value)
    }

    override fun clear() {
        lock.withLock {
            wrappedMap.clear()
            // Cancel and remove all TTL timers from the timer map.
            val ttlTimerIterator: MutableIterator<Timer> = ttlTimers.values.iterator()
            while (ttlTimerIterator.hasNext()) {
                ttlTimerIterator.next().cancel()
                ttlTimerIterator.remove()
            }
        }
    }

    /**
     * Schedules a [Timer] to remove [key] from this map after the provided [ttl] amount of time.
     */
    private fun scheduleRemoval(key: K, ttl: Long, ttlUnit: TimeUnit) {
        if (ttl != 0L) {
            val delay: Long =
                if (ttl < 0) defaultTtlMillis
                else timeInMsOrOneIfResultIsZero(time = ttl, timeUnit = ttlUnit)
            if (delay > 0) {
                val timer = Timer()
                timer.schedule(delay = delay) { remove(key = key) }
                ttlTimers[key] = timer
            }
        }
    }

    /**
     * Converts [time] to milliseconds based on the given time unit. If the conversion result is 0
     * and [time] was > 0, then 1 is returned.
     */
    private fun timeInMsOrOneIfResultIsZero(time: Long, timeUnit: TimeUnit): Long {
        var timeInMillis = timeUnit.toMillis(time)
        if (time > 0 && timeInMillis == 0L) {
            timeInMillis = 1
        }
        return timeInMillis
    }
}
