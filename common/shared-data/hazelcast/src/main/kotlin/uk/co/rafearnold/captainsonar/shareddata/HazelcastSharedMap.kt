package uk.co.rafearnold.captainsonar.shareddata

import com.hazelcast.map.IMap
import com.hazelcast.map.listener.MapListener
import java.util.*
import java.util.concurrent.TimeUnit

class HazelcastSharedMap<K : Any, V : Any>(
    private val hazelcastMap: IMap<K, V>
) : SharedMap<K, V>, MutableMap<K, V> by hazelcastMap, AbstractTtlCollection() {

    override fun put(key: K, value: V, ttl: Long, ttlUnit: TimeUnit): V? =
        hazelcastMap.put(key, value, ttl, ttlUnit)

    override fun putIfAbsent(key: K, value: V, ttl: Long, ttlUnit: TimeUnit): V? =
        hazelcastMap.putIfAbsent(key, value, ttl, ttlUnit)

    override fun addListener(handler: SharedMapEventHandler<K, V>): String {
        val hazelcastListener: MapListener = HazelcastSharedMapListener(handler = handler)
        return hazelcastMap.addEntryListener(hazelcastListener, true).toString()
    }

    override fun removeListener(listenerId: String) {
        hazelcastMap.removeEntryListener(UUID.fromString(listenerId))
    }

    override fun equals(other: Any?): Boolean = hazelcastMap == other

    override fun hashCode(): Int = hazelcastMap.hashCode()

    override fun toString(): String {
        return "HazelcastSharedMap(backingMap=${hazelcastMap.entries})"
    }
}
