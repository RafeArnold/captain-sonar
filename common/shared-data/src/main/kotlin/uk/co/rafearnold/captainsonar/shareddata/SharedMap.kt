package uk.co.rafearnold.captainsonar.shareddata

import java.util.concurrent.TimeUnit

/**
 * An extension of [MutableMap], used by [SharedDataService].
 */
interface SharedMap<K, V> : MutableMap<K, V>, TtlCollection {

    /**
     * Puts an entry into this map with a time-to-live amount. Once this amount of time has elapsed
     * since the entry was added, the entry will be removed.
     *
     * The accuracy of this time-to-live value is implementation specific.
     *
     * @param ttl The amount of time for the entry to remain in the map. Zero (0) means
     * infinite. Negative means [defaultTtlMillis] will be used instead ([ttlUnit] will be ignored
     * in this case).
     * @param ttlUnit The time unit for the TTL.
     */
    fun put(key: K, value: V, ttl: Long, ttlUnit: TimeUnit): V?

    /**
     * Puts an entry into this map with a time-to-live amount, if the specified key is not already
     * associated with a value. If the entry is added, once this amount of time has elapsed since
     * the entry was added, the entry will be removed.
     *
     * The accuracy of this time-to-live value is implementation specific.
     *
     * @param ttl The amount of time for the entry to remain in the map. Zero (0) means
     * infinite. Negative means [defaultTtlMillis] will be used instead ([ttlUnit] will be ignored
     * in this case).
     * @param ttlUnit The time unit for the TTL.
     */
    fun putIfAbsent(key: K, value: V, ttl: Long, ttlUnit: TimeUnit): V?
}
