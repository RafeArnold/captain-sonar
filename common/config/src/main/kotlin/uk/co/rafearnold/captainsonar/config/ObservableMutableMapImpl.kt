package uk.co.rafearnold.captainsonar.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.function.BiFunction
import java.util.function.Function
import java.util.function.Predicate
import kotlin.concurrent.withLock

class ObservableMutableMapImpl<K, V : Any>(
    private val backingMap: MutableMap<K, V>
) : ObservableMutableMap<K, V>, MutableMap<K, V> by backingMap {

    private val listeners: ConcurrentMap<String, ListenerConfig<K, V>> = ConcurrentHashMap()

    private val lock: Lock = ReentrantLock()

    private val listenerExecutor: Executor = Executors.newCachedThreadPool()

    override fun addListener(keyMatcher: Predicate<K>, listener: ObservableMap.Listener<K, V>): String {
        val listenerId: String = UUID.randomUUID().toString()
        listeners[listenerId] = ListenerConfig(keyMatcher = keyMatcher, listener = listener)
        return listenerId
    }

    override fun removeListener(listenerId: String) {
        listeners.remove(listenerId)
    }

    override fun clear() {
        lock.withLock {
            val copy: Map<K, V> = ConcurrentHashMap(backingMap)
            backingMap.clear()
            for ((key: K, oldValue: V) in copy) {
                handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = null))
            }
        }
    }

    override fun compute(key: K, remappingFunction: BiFunction<in K, in V?, out V?>): V? =
        lock.withLock {
            val oldValue: V? = backingMap[key]
            backingMap.compute(key, remappingFunction)
                .also { newValue: V? ->
                    handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = newValue))
                }
        }

    override fun computeIfAbsent(key: K, mappingFunction: Function<in K, out V>): V =
        lock.withLock {
            val oldValue: V? = backingMap[key]
            backingMap.computeIfAbsent(key, mappingFunction)
                .also { newValue: V? ->
                    handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = newValue))
                }
        }

    override fun computeIfPresent(key: K, remappingFunction: BiFunction<in K, in V, out V?>): V? =
        lock.withLock {
            val oldValue: V? = backingMap[key]
            backingMap.computeIfPresent(key, remappingFunction)
                .also { newValue: V? ->
                    handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = newValue))
                }
        }

    override fun merge(key: K, value: V, remappingFunction: BiFunction<in V, in V, out V?>): V? =
        lock.withLock {
            val oldValue: V? = backingMap[key]
            backingMap.merge(key, value, remappingFunction)
                .also { newValue: V? ->
                    handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = newValue))
                }
        }

    override fun put(key: K, value: V): V? =
        backingMap.put(key, value)
            .also { oldValue: V? ->
                handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = value))
            }

    override fun putAll(from: Map<out K, V>) {
        for ((key: K, value: V) in from) put(key, value)
    }

    override fun putIfAbsent(key: K, value: V): V? =
        backingMap.putIfAbsent(key, value)
            .also { oldValue: V? ->
                handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = value))
            }

    override fun remove(key: K): V? =
        backingMap.remove(key)
            .also { oldValue: V? ->
                handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = null))
            }

    override fun remove(key: K, value: V): Boolean =
        backingMap.remove(key, value)
            .also { removed: Boolean ->
                if (removed) handleEvent(event = ListenEventImpl(key = key, oldValue = value, newValue = null))
            }

    override fun replace(key: K, oldValue: V, newValue: V): Boolean =
        backingMap.replace(key, oldValue, newValue)
            .also { replaced: Boolean ->
                if (replaced) handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = newValue))
            }

    override fun replace(key: K, value: V): V? =
        backingMap.replace(key, value)
            .also { oldValue: V? ->
                if (oldValue != null)
                    handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = value))
            }

    override fun replaceAll(function: BiFunction<in K, in V, out V>) {
        lock.withLock {
            val copy: Map<K, V> = ConcurrentHashMap(backingMap)
            backingMap.replaceAll(function)
            for ((key: K, oldValue: V) in copy) {
                handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = backingMap[key]))
            }
        }
    }

    private fun handleEvent(event: ObservableMap.ListenEvent<K, V>) {
        if (event.oldValue == event.newValue) return
        for ((listenerId: String, listenerConfig: ListenerConfig<K, V>) in listeners) {
            listenerExecutor.execute {
                runCatching {
                    if (listenerConfig.keyMatcher.test(event.key)) listenerConfig.listener.handle(event)
                }.onFailure { throwable: Throwable ->
                    val errorMessage =
                        "Listener '${listenerId}' with key matcher '${listenerConfig.keyMatcher}' failed to handle event '${event}'"
                    log.error(errorMessage, throwable)
                }
            }
        }
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> = EntrySet(map = this)

    override val keys: MutableSet<K> = KeySet(map = this)

    override val values: MutableCollection<V> = ValueSet(map = this)

    private class EntrySet<K, V : Any>(
        private val map: ObservableMutableMapImpl<K, V>
    ) : MutableSet<MutableMap.MutableEntry<K, V>> by map.backingMap.entries {

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean =
            map.lock.withLock {
                val oldValue: V? = map.backingMap[element.key]
                map.backingMap.entries.add(element)
                    .also {
                        if (!it) return@also
                        val event = ListenEventImpl(key = element.key, oldValue = oldValue, newValue = element.value)
                        map.handleEvent(event = event)
                    }
            }

        override fun addAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
            map.lock.withLock {
                val copy: Map<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.entries.addAll(elements)
                    .also {
                        if (!it) return@also
                        for ((key: K, newValue: V) in elements) {
                            val event = ListenEventImpl(key = key, oldValue = copy[key], newValue = newValue)
                            map.handleEvent(event = event)
                        }
                    }
            }

        override fun clear() {
            map.lock.withLock {
                val copy: Map<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.entries.clear()
                for ((key: K, oldValue: V) in copy) {
                    map.handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = null))
                }
            }
        }

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = EntrySetIterator(map = map)

        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean =
            map.lock.withLock {
                map.backingMap.entries.remove(element)
                    .also {
                        if (!it) return@also
                        val event = ListenEventImpl(key = element.key, oldValue = element.value, newValue = null)
                        map.handleEvent(event = event)
                    }
            }

        override fun removeAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
            map.lock.withLock {
                val copy: Set<Map.Entry<K, V>> = ConcurrentHashMap(map.backingMap).entries
                map.backingMap.entries.removeAll(elements)
                    .also {
                        if (!it) return@also
                        for (entry: Map.Entry<K, V> in elements) {
                            if (entry !in copy) continue
                            val event = ListenEventImpl(key = entry.key, oldValue = entry.value, newValue = null)
                            map.handleEvent(event = event)
                        }
                    }
            }

        override fun retainAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
            map.lock.withLock {
                val copy: Map<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.entries.retainAll(elements)
                    .also {
                        if (!it) return@also
                        for (entry: Map.Entry<K, V> in copy) {
                            if (elements.contains(entry)) continue
                            val event = ListenEventImpl(key = entry.key, oldValue = entry.value, newValue = null)
                            map.handleEvent(event = event)
                        }
                    }
            }

        override fun removeIf(filter: Predicate<in MutableMap.MutableEntry<K, V>>): Boolean =
            map.lock.withLock {
                val copy: Map<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.entries.removeIf(filter)
                    .also {
                        if (!it) return@also
                        val backingEntries: Set<Map.Entry<K, V>> = map.backingMap.entries
                        for (entry: Map.Entry<K, V> in copy) {
                            if (entry in backingEntries) continue
                            val event = ListenEventImpl(key = entry.key, oldValue = entry.value, newValue = null)
                            map.handleEvent(event = event)
                        }
                    }
            }

        private class EntrySetIterator<K, V : Any>(
            private val map: ObservableMutableMapImpl<K, V>
        ) : MutableIterator<MutableMap.MutableEntry<K, V>> {

            private val backingIterator: MutableIterator<MutableMap.MutableEntry<K, V>> =
                map.backingMap.entries.iterator()

            private var currentEntry: MutableMap.MutableEntry<K, V>? = null

            override fun hasNext(): Boolean = backingIterator.hasNext()

            override fun next(): MutableMap.MutableEntry<K, V> =
                map.lock.withLock {
                    val entry: MutableMap.MutableEntry<K, V> = backingIterator.next()
                    this.currentEntry = entry
                    EntrySetEntry(map = map, backingEntry = entry)
                }

            override fun remove() =
                map.lock.withLock {
                    backingIterator.remove()
                    val entry: MutableMap.MutableEntry<K, V>? = currentEntry
                    if (entry != null) {
                        val event = ListenEventImpl(key = entry.key, oldValue = entry.value, newValue = null)
                        map.handleEvent(event = event)
                    }
                }
        }

        private class EntrySetEntry<K, V : Any>(
            private val map: ObservableMutableMapImpl<K, V>,
            private val backingEntry: MutableMap.MutableEntry<K, V>
        ) : MutableMap.MutableEntry<K, V> {

            override val key: K get() = backingEntry.key

            override val value: V get() = backingEntry.value

            override fun setValue(newValue: V): V =
                backingEntry.setValue(newValue)
                    .also { oldValue: V ->
                        val event = ListenEventImpl(key = key, oldValue = oldValue, newValue = value)
                        map.handleEvent(event = event)
                    }

            override fun equals(other: Any?): Boolean = backingEntry == other

            override fun hashCode(): Int = backingEntry.hashCode()

            override fun toString(): String = backingEntry.toString()
        }

        override fun equals(other: Any?): Boolean = map.backingMap.entries == other

        override fun hashCode(): Int = map.backingMap.entries.hashCode()

        override fun toString(): String = map.backingMap.entries.toString()
    }

    private class KeySet<K, V : Any>(
        private val map: ObservableMutableMapImpl<K, V>
    ) : MutableSet<K> by map.backingMap.keys {

        override fun add(element: K): Boolean =
            map.lock.withLock {
                val oldValue: V? = map.backingMap[element]
                map.backingMap.keys.add(element)
                    .also {
                        if (!it) return@also
                        val event =
                            ListenEventImpl(key = element, oldValue = oldValue, newValue = map.backingMap[element])
                        map.handleEvent(event = event)
                    }
            }

        override fun addAll(elements: Collection<K>): Boolean =
            map.lock.withLock {
                val copy: Map<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.keys.addAll(elements)
                    .also {
                        if (!it) return@also
                        for (key: K in elements) {
                            val event = ListenEventImpl(key = key, oldValue = copy[key], newValue = map.backingMap[key])
                            map.handleEvent(event = event)
                        }
                    }
            }

        override fun clear() {
            map.lock.withLock {
                val copy: Map<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.keys.clear()
                for ((key: K, oldValue: V) in copy) {
                    map.handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = null))
                }
            }
        }

        override fun iterator(): MutableIterator<K> = KeySetIterator(map = map)

        override fun remove(element: K): Boolean =
            map.lock.withLock {
                val oldValue: V? = map.backingMap[element]
                map.backingMap.keys.remove(element)
                    .also {
                        if (!it) return@also
                        val event = ListenEventImpl(key = element, oldValue = oldValue, newValue = null)
                        map.handleEvent(event = event)
                    }
            }

        override fun removeAll(elements: Collection<K>): Boolean =
            map.lock.withLock {
                val copy: Map<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.keys.removeAll(elements)
                    .also {
                        if (!it) return@also
                        for (key: K in elements) {
                            val oldValue: V = copy[key] ?: continue
                            map.handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = null))
                        }
                    }
            }

        override fun retainAll(elements: Collection<K>): Boolean =
            map.lock.withLock {
                val copy: Map<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.keys.retainAll(elements)
                    .also {
                        if (!it) return@also
                        for ((key: K, value: V) in copy) {
                            if (elements.contains(key)) continue
                            map.handleEvent(event = ListenEventImpl(key = key, oldValue = value, newValue = null))
                        }
                    }
            }

        override fun removeIf(filter: Predicate<in K>): Boolean =
            map.lock.withLock {
                val copy: Map<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.keys.removeIf(filter)
                    .also {
                        if (!it) return@also
                        for ((key: K, oldValue: V) in copy) {
                            if (map.backingMap.containsKey(key)) continue
                            map.handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = null))
                        }
                    }
            }

        private class KeySetIterator<K, V : Any>(
            private val map: ObservableMutableMapImpl<K, V>
        ) : MutableIterator<K> {

            private val backingIterator: MutableIterator<K> = map.backingMap.keys.iterator()

            private var currentKey: K? = null

            override fun hasNext(): Boolean = backingIterator.hasNext()

            override fun next(): K =
                map.lock.withLock {
                    val key: K = backingIterator.next()
                    this.currentKey = key
                    key
                }

            override fun remove() =
                map.lock.withLock {
                    val oldValue: V? = map[currentKey]
                    backingIterator.remove()
                    val key: K? = currentKey
                    if (key != null) {
                        val event: ListenEventImpl<K, V> =
                            ListenEventImpl(key = key, oldValue = oldValue, newValue = null)
                        map.handleEvent(event = event)
                    }
                }
        }

        override fun equals(other: Any?): Boolean = map.backingMap.keys == other

        override fun hashCode(): Int = map.backingMap.keys.hashCode()

        override fun toString(): String = map.backingMap.keys.toString()
    }

    private class ValueSet<K, V : Any>(
        private val map: ObservableMutableMapImpl<K, V>
    ) : MutableCollection<V> by map.backingMap.values {

        override fun clear() {
            map.lock.withLock {
                val copy: Map<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.values.clear()
                for ((key: K, oldValue: V) in copy) {
                    map.handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = null))
                }
            }
        }

        override fun iterator(): MutableIterator<V> = ValueSetIterator(map = map)

        override fun remove(element: V): Boolean =
            map.lock.withLock {
                val copy: MutableMap<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.values.remove(element)
                    .also {
                        if (!it) return@also
                        copy.keys.removeAll(map.backingMap.keys)
                        for ((key: K, oldValue: V) in copy) {
                            map.handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = null))
                        }
                    }
            }

        override fun removeAll(elements: Collection<V>): Boolean =
            map.lock.withLock {
                val copy: MutableMap<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.values.removeAll(elements)
                    .also {
                        if (!it) return@also
                        copy.keys.removeAll(map.backingMap.keys)
                        for ((key: K, oldValue: V) in copy) {
                            map.handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = null))
                        }
                    }
            }

        override fun retainAll(elements: Collection<V>): Boolean =
            map.lock.withLock {
                val copy: MutableMap<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.values.retainAll(elements)
                    .also {
                        if (!it) return@also
                        copy.keys.removeAll(map.backingMap.keys)
                        for ((key: K, oldValue: V) in copy) {
                            map.handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = null))
                        }
                    }
            }

        override fun removeIf(filter: Predicate<in V>): Boolean =
            map.lock.withLock {
                val copy: MutableMap<K, V> = ConcurrentHashMap(map.backingMap)
                map.backingMap.values.removeIf(filter)
                    .also {
                        if (!it) return@also
                        copy.keys.removeAll(map.backingMap.keys)
                        for ((key: K, oldValue: V) in copy) {
                            map.handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = null))
                        }
                    }
            }

        private class ValueSetIterator<K, V : Any>(
            private val map: ObservableMutableMapImpl<K, V>
        ) : MutableIterator<V> {

            private val backingIterator: MutableIterator<V> = map.backingMap.values.iterator()

            private var currentValue: V? = null

            override fun hasNext(): Boolean = backingIterator.hasNext()

            override fun next(): V =
                map.lock.withLock {
                    val value: V = backingIterator.next()
                    this.currentValue = value
                    value
                }

            override fun remove() =
                map.lock.withLock {
                    val copy: MutableMap<K, V> = ConcurrentHashMap(map.backingMap)
                    backingIterator.remove()
                    copy.keys.removeAll(map.backingMap.keys)
                    for ((key: K, oldValue: V) in copy) {
                        map.handleEvent(event = ListenEventImpl(key = key, oldValue = oldValue, newValue = null))
                    }
                }
        }

        override fun equals(other: Any?): Boolean = map.backingMap.keys == other

        override fun hashCode(): Int = map.backingMap.keys.hashCode()

        override fun toString(): String = map.backingMap.keys.toString()
    }

    override fun equals(other: Any?): Boolean = backingMap == other

    override fun hashCode(): Int = backingMap.hashCode()

    override fun toString(): String = backingMap.toString()

    private data class ListenerConfig<K, V>(val keyMatcher: Predicate<K>, val listener: ObservableMap.Listener<K, V>)

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ObservableMutableMapImpl::class.java)
    }
}
