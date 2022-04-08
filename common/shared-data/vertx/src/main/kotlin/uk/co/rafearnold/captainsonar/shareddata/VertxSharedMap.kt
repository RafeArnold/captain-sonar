package uk.co.rafearnold.captainsonar.shareddata

import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.shareddata.AsyncMap
import uk.co.rafearnold.captainsonar.common.toCompletableFuture
import java.util.concurrent.TimeUnit

class VertxSharedMap<K, V>(
    private val asyncMap: AsyncMap<K, V>,
    private val lock: SharedLock
) : SharedMap<K, V>, AbstractTtlCollection() {

    private val backingMap: Map<K, V> get() = asyncMap.entries().toCompletableFuture().get()

    override val size: Int get() = backingMap.size

    override fun containsKey(key: K): Boolean = backingMap.containsKey(key)

    override fun containsValue(value: V): Boolean = backingMap.containsValue(value)

    override fun get(key: K): V? = asyncMap[key].toCompletableFuture().get()

    override fun isEmpty(): Boolean = backingMap.isEmpty()

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> = EntrySet(map = this)

    override val keys: MutableSet<K> = KeySet(map = this)

    override val values: MutableCollection<V> = ValueCollection(map = this)

    override fun clear() {
        asyncMap.clear().toCompletableFuture().get()
    }

    override fun put(key: K, value: V): V? =
        lock.withLock {
            val oldValue: V? = get(key)
            asyncMap.put(key, value).toCompletableFuture().get()
            oldValue
        }

    override fun putAll(from: Map<out K, V>) {
        lock.withLock {
            CompositeFuture.all(from.map { (key: K, value: V) -> asyncMap.put(key, value) }).toCompletableFuture().get()
        }
    }

    override fun remove(key: K): V? = asyncMap.remove(key).toCompletableFuture().get()

    override fun put(key: K, value: V, ttl: Long, ttlUnit: TimeUnit): V? =
        lock.withLock {
            val oldValue: V? = get(key)
            asyncMap.put(key, value, ttlUnit.toMillis(ttl)).toCompletableFuture().get()
            oldValue
        }

    override fun putIfAbsent(key: K, value: V, ttl: Long, ttlUnit: TimeUnit): V? =
        asyncMap.putIfAbsent(key, value, ttlUnit.toMillis(ttl)).toCompletableFuture().get()

    private class EntrySet<K, V>(
        private val map: VertxSharedMap<K, V>
    ) : MutableSet<MutableMap.MutableEntry<K, V>> {

        override fun add(element: MutableMap.MutableEntry<K, V>): Boolean =
            map.put(element.key, element.value) != element.value

        override fun addAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
            map.lock.withLock {
                var modified = false
                val futures: List<Future<*>> =
                    elements.map { (key: K, value: V) ->
                        map.asyncMap.put(key, value).map { if (it != value) modified = true }
                    }
                CompositeFuture.all(futures).toCompletableFuture().get()
                modified
            }

        override fun clear() = map.clear()

        override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> = EntrySetIterator(map = map)

        override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean = map.remove(element.key, element.value)

        override fun removeAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
            map.lock.withLock {
                var modified = false
                val futures: List<Future<*>> =
                    elements.map { (key: K, value: V) ->
                        map.asyncMap.removeIfPresent(key, value).map { if (it) modified = true }
                    }
                CompositeFuture.all(futures).toCompletableFuture().get()
                modified
            }

        override fun retainAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
            map.lock.withLock {
                var modified = false
                map.asyncMap.entries()
                    .compose { entries: MutableMap<K, V> ->
                        val futures: List<Future<*>> =
                            entries.entries.map { entry: MutableMap.MutableEntry<K, V> ->
                                if (!elements.contains(entry)) {
                                    map.asyncMap.remove(entry.key)
                                        .map { if (it != null) modified = true; it }
                                } else Future.succeededFuture()
                            }
                        CompositeFuture.all(futures)
                    }
                    .toCompletableFuture().get()
                modified
            }

        override val size: Int get() = map.size

        override fun contains(element: MutableMap.MutableEntry<K, V>): Boolean = map[element.key] == element.value

        override fun containsAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
            map.lock.withLock {
                for ((key: K, value: V) in elements) if (map[key] != value) return false
                return true
            }

        override fun isEmpty(): Boolean = map.isEmpty()

        private class EntrySetIterator<K, V>(
            private val map: VertxSharedMap<K, V>
        ) : MutableIterator<MutableMap.MutableEntry<K, V>> {

            private val backingIterator: Iterator<Map.Entry<K, V>> = map.backingMap.entries.iterator()

            private var currentEntry: Map.Entry<K, V>? = null

            override fun hasNext(): Boolean = backingIterator.hasNext()

            override fun next(): MutableMap.MutableEntry<K, V> =
                map.lock.withLock {
                    val entry: Map.Entry<K, V> = backingIterator.next()
                    currentEntry = entry
                    EntrySetIteratorEntry(map = map, backingEntry = entry)
                }

            override fun remove() {
                val entry: Map.Entry<K, V>? = currentEntry
                if (entry != null) map.remove(entry.key)
            }

            private class EntrySetIteratorEntry<K, V>(
                private val map: VertxSharedMap<K, V>,
                private val backingEntry: Map.Entry<K, V>
            ) : MutableMap.MutableEntry<K, V> {

                override val key: K get() = backingEntry.key

                override val value: V get() = backingEntry.value

                override fun setValue(newValue: V): V {
                    map[key] = newValue
                    return value
                }
            }
        }
    }

    private class KeySet<K, V>(
        private val map: VertxSharedMap<K, V>
    ) : MutableSet<K> {

        override fun add(element: K): Boolean = throw UnsupportedOperationException()

        override fun addAll(elements: Collection<K>): Boolean = throw UnsupportedOperationException()

        override fun clear() = map.clear()

        override fun iterator(): MutableIterator<K> = KeySetIterator(map = map)

        override fun remove(element: K): Boolean = map.remove(element) != null

        override fun removeAll(elements: Collection<K>): Boolean =
            map.lock.withLock {
                var modified = false
                val futures: List<Future<*>> =
                    elements.map { element: K ->
                        map.asyncMap.remove(element).map { if (it != null) modified = true }
                    }
                CompositeFuture.all(futures).toCompletableFuture().get()
                modified
            }

        override fun retainAll(elements: Collection<K>): Boolean =
            map.lock.withLock {
                var modified = false
                map.asyncMap.entries()
                    .compose { entries: MutableMap<K, V> ->
                        val futures: List<Future<*>> =
                            entries.keys.map { key: K ->
                                if (!elements.contains(key)) {
                                    map.asyncMap.remove(key)
                                        .map { if (it != null) modified = true; it }
                                } else Future.succeededFuture()
                            }
                        CompositeFuture.all(futures)
                    }
                    .toCompletableFuture().get()
                modified
            }

        override val size: Int get() = map.size

        override fun contains(element: K): Boolean = map.containsKey(element)

        override fun containsAll(elements: Collection<K>): Boolean = map.backingMap.keys.containsAll(elements)

        override fun isEmpty(): Boolean = map.isEmpty()

        private class KeySetIterator<K, V>(
            map: VertxSharedMap<K, V>
        ) : MutableIterator<K> {

            private val backingIterator: MutableIterator<Map.Entry<K, V>> = map.entries.iterator()

            override fun hasNext(): Boolean = backingIterator.hasNext()

            override fun next(): K = backingIterator.next().key

            override fun remove() = backingIterator.remove()
        }
    }

    private class ValueCollection<K, V>(
        private val map: VertxSharedMap<K, V>
    ) : MutableCollection<V> {

        override fun add(element: V): Boolean = throw UnsupportedOperationException()

        override fun addAll(elements: Collection<V>): Boolean = throw UnsupportedOperationException()

        override fun clear() = map.clear()

        override fun iterator(): MutableIterator<V> = ValueSetIterator(map = map)

        override fun remove(element: V): Boolean =
            map.lock.withLock {
                val iterator: MutableIterator<V> = iterator()
                while (iterator.hasNext()) {
                    if (element == iterator.next()) {
                        iterator.remove()
                        return true
                    }
                }
                return false
            }

        override fun removeAll(elements: Collection<V>): Boolean =
            map.lock.withLock {
                var modified = false
                val iterator: MutableIterator<V> = iterator()
                while (iterator.hasNext()) {
                    if (elements.contains(iterator.next())) {
                        iterator.remove()
                        modified = true
                    }
                }
                modified
            }

        override fun retainAll(elements: Collection<V>): Boolean =
            map.lock.withLock {
                var modified = false
                val iterator: MutableIterator<V> = iterator()
                while (iterator.hasNext()) {
                    if (!elements.contains(iterator.next())) {
                        iterator.remove()
                        modified = true
                    }
                }
                return modified
            }

        override val size: Int get() = map.size

        override fun contains(element: V): Boolean = map.containsValue(element)

        override fun containsAll(elements: Collection<V>): Boolean =
            map.lock.withLock {
                val backingMap: Map<K, V> = map.backingMap
                for (value: V in elements) if (!backingMap.containsValue(value)) return false
                return true
            }

        override fun isEmpty(): Boolean = map.isEmpty()

        private class ValueSetIterator<K, V>(
            map: VertxSharedMap<K, V>
        ) : MutableIterator<V> {

            private val backingIterator: MutableIterator<Map.Entry<K, V>> = map.entries.iterator()

            override fun hasNext(): Boolean = backingIterator.hasNext()

            override fun next(): V = backingIterator.next().value

            override fun remove() = backingIterator.remove()
        }
    }

    override fun equals(other: Any?): Boolean = backingMap == other

    override fun hashCode(): Int = backingMap.hashCode()

    override fun toString(): String {
        return "VertxSharedMap(backingMap=$backingMap)"
    }
}
