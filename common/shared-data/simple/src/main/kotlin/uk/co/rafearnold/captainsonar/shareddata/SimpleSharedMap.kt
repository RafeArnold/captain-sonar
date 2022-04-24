package uk.co.rafearnold.captainsonar.shareddata

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.common.alwaysTrue
import uk.co.rafearnold.captainsonar.config.ObservableMutableMap
import uk.co.rafearnold.captainsonar.config.ObservableMutableMapImpl
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Predicate
import kotlin.concurrent.schedule
import kotlin.concurrent.withLock

/**
 * Basic implementation of [SharedMap] that implements TTL on its entry.
 */
class SimpleSharedMap<K, V : Any> private constructor(
    private val unobservedWrappedMap: MutableMap<K, V>,
    private val wrappedMap: ObservableMutableMap<K, V>
) : SharedMap<K, V>, MutableMap<K, V> by wrappedMap, AbstractTtlCollection() {

    constructor(
        wrappedMap: MutableMap<K, V> = ConcurrentHashMap()
    ) : this(unobservedWrappedMap = wrappedMap, wrappedMap = ObservableMutableMapImpl(backingMap = wrappedMap))

    private val ttlTimers: MutableMap<K, Timer> = ConcurrentHashMap()

    private val lock: Lock = ReentrantLock()

    private val listeners: MutableMap<String, SharedMapEventHandler<K, V>> = ConcurrentHashMap()

    private val listenerHandlerExecutor: Executor = Executors.newCachedThreadPool()

    override fun put(key: K, value: V, ttl: Long, ttlUnit: TimeUnit): V? =
        lock.withLock {
            val oldValue: V? = wrappedMap.put(key = key, value = value)
            rescheduleRemoval(key = key, ttl = ttl, ttlUnit = ttlUnit)
            oldValue
        }

    override fun putIfAbsent(key: K, value: V, ttl: Long, ttlUnit: TimeUnit): V? =
        lock.withLock {
            val currentValue: V? = wrappedMap.putIfAbsent(key, value)
            if (currentValue == null) {
                rescheduleRemoval(key = key, ttl = ttl, ttlUnit = ttlUnit)
            }
            currentValue
        }

    override fun addListener(handler: SharedMapEventHandler<K, V>): String {
        val listenerId: String =
            wrappedMap.addListener(alwaysTrue()) {
                val oldValue: V? = it.oldValue
                val newValue: V? = it.newValue
                val event: SharedMapEvent<K, V>? =
                    if (newValue != null) {
                        if (oldValue == null) EntryAddedEvent(key = it.key, newValue = newValue)
                        else EntryUpdatedEvent(key = it.key, oldValue = oldValue, newValue = newValue)
                    } else if (oldValue != null) EntryRemovedEvent(key = it.key, oldValue = oldValue)
                    else null
                if (event != null) handler.handle(event = event)
            }
        listeners[listenerId] = handler
        return listenerId
    }

    override fun removeListener(listenerId: String) {
        wrappedMap.removeListener(listenerId = listenerId)
        listeners.remove(listenerId)
    }

    override fun put(key: K, value: V): V? =
        put(key = key, value = value, ttl = defaultTtlMillis, ttlUnit = TimeUnit.MILLISECONDS)

    override fun putIfAbsent(key: K, value: V): V? =
        putIfAbsent(key = key, value = value, ttl = defaultTtlMillis, ttlUnit = TimeUnit.MILLISECONDS)

    override fun remove(key: K): V? =
        lock.withLock {
            cancelScheduledRemoval(key = key)
            wrappedMap.remove(key = key)
        }

    override fun remove(key: K, value: V): Boolean =
        lock.withLock {
            val removed: Boolean = wrappedMap.remove(key = key, value = value)
            if (removed) {
                cancelScheduledRemoval(key = key)
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

    override fun compute(key: K, remappingFunction: BiFunction<in K, in V?, out V?>): V? =
        lock.withLock { wrappedMap.compute(key, remappingFunction).also { rescheduleRemoval(key = key) } }

    override fun computeIfAbsent(key: K, mappingFunction: Function<in K, out V>): V =
        lock.withLock {
            val wasAbsent: Boolean = !wrappedMap.containsKey(key)
            wrappedMap.computeIfAbsent(key, mappingFunction).also { if (wasAbsent) rescheduleRemoval(key = key) }
        }

    override fun computeIfPresent(key: K, remappingFunction: BiFunction<in K, in V, out V?>): V? =
        lock.withLock {
            val wasPresent: Boolean = wrappedMap.containsKey(key)
            wrappedMap.computeIfPresent(key, remappingFunction).also { if (wasPresent) rescheduleRemoval(key = key) }
        }

    override fun merge(key: K, value: V, remappingFunction: BiFunction<in V, in V, out V?>): V? =
        lock.withLock { wrappedMap.merge(key, value, remappingFunction).also { rescheduleRemoval(key = key) } }

    override fun replace(key: K, oldValue: V, newValue: V): Boolean =
        lock.withLock {
            wrappedMap.replace(key, oldValue, newValue)
                .also { wasReplaced: Boolean -> if (wasReplaced) rescheduleRemoval(key = key) }
        }

    override fun replace(key: K, value: V): V? =
        lock.withLock {
            wrappedMap.replace(key, value)
                .also { previousValue: V? -> if (previousValue != null) rescheduleRemoval(key = key) }
        }

    override fun replaceAll(function: BiFunction<in K, in V, out V>) =
        lock.withLock {
            val copy: Map<K, V> = ConcurrentHashMap(wrappedMap)
            wrappedMap.replaceAll(function)
            for (key: K in copy.keys) rescheduleRemoval(key = key)
        }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> = EntrySet(map = this)

    override val keys: MutableSet<K> = KeySet(map = this)

    override val values: MutableCollection<V> = ValueCollection(map = this)

    class EntrySet<K, V : Any>(
        private val map: SimpleSharedMap<K, V>
    ) : MutableSet<MutableMap.MutableEntry<K, V>> by map.wrappedMap.entries {

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean =
            map.lock.withLock {
                map.wrappedMap.entries.add(element)
                    .also { wasAdded: Boolean -> if (wasAdded) map.rescheduleRemoval(key = element.key) }
            }

        override fun addAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
            map.lock.withLock {
                val copy: Set<Map.Entry<K, V>> = ConcurrentHashMap(map.wrappedMap).entries
                map.wrappedMap.entries.addAll(elements)
                    .also { wasModified: Boolean ->
                        if (wasModified) {
                            for (entry: Map.Entry<K, V> in elements) {
                                if (entry !in copy) map.rescheduleRemoval(key = entry.key)
                            }
                        }
                    }
            }

        override fun clear() = map.clear()

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = Iterator(map = map)

        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean =
            map.lock.withLock {
                map.wrappedMap.entries.remove(element).also { map.cancelScheduledRemoval(key = element.key) }
            }

        override fun removeAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
            map.lock.withLock {
                val copy: Set<Map.Entry<K, V>> = ConcurrentHashMap(map.wrappedMap).entries
                map.wrappedMap.entries.removeAll(elements)
                    .also { wasModified: Boolean ->
                        if (wasModified) {
                            for (entry: Map.Entry<K, V> in copy) {
                                if (entry !in map.wrappedMap.entries) map.cancelScheduledRemoval(key = entry.key)
                            }
                        }
                    }
            }

        override fun retainAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
            map.lock.withLock {
                val copy: Set<Map.Entry<K, V>> = ConcurrentHashMap(map.wrappedMap).entries
                map.wrappedMap.entries.retainAll(elements)
                    .also { wasModified: Boolean ->
                        if (wasModified) {
                            for (entry: Map.Entry<K, V> in copy) {
                                if (entry !in map.wrappedMap.entries) map.cancelScheduledRemoval(key = entry.key)
                            }
                        }
                    }
            }

        override fun removeIf(filter: Predicate<in MutableMap.MutableEntry<K, V>>): Boolean =
            map.lock.withLock {
                val copy: Set<Map.Entry<K, V>> = ConcurrentHashMap(map.wrappedMap).entries
                map.wrappedMap.entries.removeIf(filter)
                    .also { wasModified: Boolean ->
                        if (wasModified) {
                            for (entry: Map.Entry<K, V> in copy) {
                                if (entry !in map.wrappedMap.entries) map.cancelScheduledRemoval(key = entry.key)
                            }
                        }
                    }
            }

        private class Iterator<K, V : Any>(
            private val map: SimpleSharedMap<K, V>
        ) : MutableIterator<MutableMap.MutableEntry<K, V>> {

            private val backingIterator: MutableIterator<MutableMap.MutableEntry<K, V>> =
                map.wrappedMap.entries.iterator()

            private var currentEntry: MutableMap.MutableEntry<K, V>? = null

            override fun hasNext(): Boolean = backingIterator.hasNext()

            override fun next(): MutableMap.MutableEntry<K, V> =
                map.lock.withLock {
                    val entry: MutableMap.MutableEntry<K, V> = backingIterator.next()
                    currentEntry = entry
                    Entry(map = map, backingEntry = entry)
                }

            override fun remove() =
                map.lock.withLock {
                    backingIterator.remove()
                    val entry: MutableMap.MutableEntry<K, V>? = currentEntry
                    if (entry != null) map.cancelScheduledRemoval(key = entry.key)
                }

            private class Entry<K, V : Any>(
                private val map: SimpleSharedMap<K, V>,
                private val backingEntry: MutableMap.MutableEntry<K, V>
            ) : MutableMap.MutableEntry<K, V> {

                override val key: K get() = backingEntry.key

                override val value: V get() = backingEntry.value

                override fun setValue(newValue: V): V =
                    map.lock.withLock { backingEntry.setValue(newValue).also { map.rescheduleRemoval(key = key) } }
            }
        }

        override fun equals(other: Any?): Boolean = map.wrappedMap.entries == other

        override fun hashCode(): Int = map.wrappedMap.entries.hashCode()

        override fun toString(): String = "EntrySet(map=$map)"
    }

    class KeySet<K, V : Any>(
        private val map: SimpleSharedMap<K, V>
    ) : MutableSet<K> by map.wrappedMap.keys {

        override fun add(element: K): Boolean =
            map.lock.withLock {
                map.wrappedMap.keys.add(element)
                    .also { wasAdded: Boolean -> if (wasAdded) map.rescheduleRemoval(key = element) }
            }

        override fun addAll(elements: Collection<K>): Boolean =
            map.lock.withLock {
                val copy: Set<K> = ConcurrentHashMap(map.wrappedMap).keys
                map.wrappedMap.keys.addAll(elements)
                    .also { wasModified: Boolean ->
                        if (wasModified) {
                            for (key: K in elements) {
                                if (key !in copy) map.rescheduleRemoval(key = key)
                            }
                        }
                    }
            }

        override fun clear() = map.clear()

        override fun iterator(): MutableIterator<K> = Iterator(map = map)

        override fun remove(element: K): Boolean =
            map.lock.withLock {
                map.wrappedMap.keys.remove(element).also { map.cancelScheduledRemoval(key = element) }
            }

        override fun removeAll(elements: Collection<K>): Boolean =
            map.lock.withLock {
                val copy: Set<K> = ConcurrentHashMap(map.wrappedMap).keys
                map.wrappedMap.keys.removeAll(elements)
                    .also { wasModified: Boolean ->
                        if (wasModified) {
                            for (key: K in copy) {
                                if (key !in map.wrappedMap.keys) map.cancelScheduledRemoval(key = key)
                            }
                        }
                    }
            }

        override fun retainAll(elements: Collection<K>): Boolean =
            map.lock.withLock {
                val copy: Set<K> = ConcurrentHashMap(map.wrappedMap).keys
                map.wrappedMap.keys.retainAll(elements)
                    .also { wasModified: Boolean ->
                        if (wasModified) {
                            for (key: K in copy) {
                                if (key !in map.wrappedMap.keys) map.cancelScheduledRemoval(key = key)
                            }
                        }
                    }
            }

        override fun removeIf(filter: Predicate<in K>): Boolean =
            map.lock.withLock {
                val copy: Set<K> = ConcurrentHashMap(map.wrappedMap).keys
                map.wrappedMap.keys.removeIf(filter)
                    .also { wasModified: Boolean ->
                        if (wasModified) {
                            for (key: K in copy) {
                                if (key !in map.wrappedMap.keys) map.cancelScheduledRemoval(key = key)
                            }
                        }
                    }
            }

        private class Iterator<K, V : Any>(
            private val map: SimpleSharedMap<K, V>
        ) : MutableIterator<K> {

            private val backingIterator: MutableIterator<MutableMap.MutableEntry<K, V>> =
                map.wrappedMap.entries.iterator()

            private var currentEntry: MutableMap.MutableEntry<K, V>? = null

            override fun hasNext(): Boolean = backingIterator.hasNext()

            override fun next(): K =
                map.lock.withLock {
                    val entry: MutableMap.MutableEntry<K, V> = backingIterator.next()
                    currentEntry = entry
                    entry.key
                }

            override fun remove() =
                map.lock.withLock {
                    backingIterator.remove()
                    val entry: MutableMap.MutableEntry<K, V>? = currentEntry
                    if (entry != null) map.cancelScheduledRemoval(key = entry.key)
                }
        }

        override fun equals(other: Any?): Boolean = map.wrappedMap.keys == other

        override fun hashCode(): Int = map.wrappedMap.keys.hashCode()

        override fun toString(): String = "KeySet(map=$map)"
    }

    class ValueCollection<K, V : Any>(
        private val map: SimpleSharedMap<K, V>
    ) : MutableCollection<V> by map.wrappedMap.values {

        override fun clear() = map.clear()

        override fun iterator(): MutableIterator<V> = Iterator(map = map)

        override fun remove(element: V): Boolean =
            map.lock.withLock {
                val copy: MutableMap<K, V> = ConcurrentHashMap(map.wrappedMap)
                map.wrappedMap.values.remove(element)
                    .also { wasRemoved: Boolean ->
                        if (wasRemoved) {
                            copy.keys.removeAll(map.wrappedMap.keys)
                            for (key: K in copy.keys) {
                                map.cancelScheduledRemoval(key = key)
                            }
                        }
                    }
            }

        override fun removeAll(elements: Collection<V>): Boolean =
            map.lock.withLock {
                val copy: MutableMap<K, V> = ConcurrentHashMap(map.wrappedMap)
                map.wrappedMap.values.removeAll(elements)
                    .also { wasModified: Boolean ->
                        if (wasModified) {
                            copy.keys.removeAll(map.wrappedMap.keys)
                            for (key: K in copy.keys) {
                                map.cancelScheduledRemoval(key = key)
                            }
                        }
                    }
            }

        override fun retainAll(elements: Collection<V>): Boolean =
            map.lock.withLock {
                val copy: MutableMap<K, V> = ConcurrentHashMap(map.wrappedMap)
                map.wrappedMap.values.retainAll(elements)
                    .also { wasModified: Boolean ->
                        if (wasModified) {
                            copy.keys.removeAll(map.wrappedMap.keys)
                            for (key: K in copy.keys) {
                                map.cancelScheduledRemoval(key = key)
                            }
                        }
                    }
            }

        override fun removeIf(filter: Predicate<in V>): Boolean =
            map.lock.withLock {
                val copy: MutableMap<K, V> = ConcurrentHashMap(map.wrappedMap)
                map.wrappedMap.values.removeIf(filter)
                    .also { wasModified: Boolean ->
                        if (wasModified) {
                            copy.keys.removeAll(map.wrappedMap.keys)
                            for (key: K in copy.keys) {
                                map.cancelScheduledRemoval(key = key)
                            }
                        }
                    }
            }

        private class Iterator<K, V : Any>(
            private val map: SimpleSharedMap<K, V>
        ) : MutableIterator<V> {

            private val backingIterator: MutableIterator<MutableMap.MutableEntry<K, V>> =
                map.wrappedMap.entries.iterator()

            private var currentEntry: MutableMap.MutableEntry<K, V>? = null

            override fun hasNext(): Boolean = backingIterator.hasNext()

            override fun next(): V =
                map.lock.withLock {
                    val entry: MutableMap.MutableEntry<K, V> = backingIterator.next()
                    currentEntry = entry
                    entry.value
                }

            override fun remove() =
                map.lock.withLock {
                    backingIterator.remove()
                    val entry: MutableMap.MutableEntry<K, V>? = currentEntry
                    if (entry != null) map.cancelScheduledRemoval(key = entry.key)
                }
        }

        override fun equals(other: Any?): Boolean = map.wrappedMap.values == other

        override fun hashCode(): Int = map.wrappedMap.values.hashCode()

        override fun toString(): String = "ValueCollection(map=$map)"
    }

    private fun rescheduleRemoval(key: K, ttl: Long = defaultTtlMillis, ttlUnit: TimeUnit = TimeUnit.MILLISECONDS) {
        cancelScheduledRemoval(key = key)
        scheduleRemoval(key = key, ttl = ttl, ttlUnit = ttlUnit)
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
                timer.schedule(delay = delay) {
                    lock.withLock {
                        // The timer may have been removed but not yet cancelled.
                        if (timer !in ttlTimers.values) return@schedule
                        cancelScheduledRemoval(key = key)
                        // Remove from the unobserved map to prevent an entry-removed event being trigger.
                        val oldValue: V? = unobservedWrappedMap.remove(key = key)
                        if (oldValue != null) {
                            val expiredEvent = EntryExpiredEvent(key = key, oldValue = oldValue)
                            runListenerHandlers(event = expiredEvent)
                        }
                    }
                }
                ttlTimers[key] = timer
            }
        }
    }

    private fun cancelScheduledRemoval(key: K) {
        ttlTimers.remove(key)?.cancel()
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

    private fun runListenerHandlers(event: SharedMapEvent<K, V>) {
        for ((listenerId: String, handler: SharedMapEventHandler<K, V>) in listeners) {
            listenerHandlerExecutor.execute {
                runCatching { handler.handle(event) }
                    .onFailure { log.error("Listener '$listenerId' failed to handle event '$event'", it) }
            }
        }
    }

    override fun equals(other: Any?): Boolean = wrappedMap == other

    override fun hashCode(): Int = wrappedMap.hashCode()

    override fun toString(): String = "SimpleSharedMap(wrappedMap=$wrappedMap)"

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SimpleSharedMap::class.java)
    }
}
