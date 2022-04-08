package uk.co.rafearnold.captainsonar.shareddata

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * Abstract implementation of [SharedDataService].
 *
 * This implementation handles the local data, since a simple implementation of these functions is
 * possible without the use of a third-party library.
 */
abstract class AbstractSharedDataService : SharedDataService {

    private val localLongs: MutableMap<String, SharedAtomicLong> = ConcurrentHashMap()

    private val localLocks: MutableMap<String, SharedLock> = ConcurrentHashMap()

    private val localMaps: MutableMap<String, SharedMap<*, *>> = ConcurrentHashMap()

    override fun getLong(name: String, localOnly: Boolean): SharedAtomicLong =
        if (localOnly) getLocalLong(name) else getDistributedLong(name)

    override fun getLock(name: String, localOnly: Boolean): SharedLock =
        if (localOnly) getLocalLock(name = name) else getDistributedLock(name = name)

    override fun <K : Any, V : Any> getMap(name: String, localOnly: Boolean): SharedMap<K, V> =
        if (localOnly) getLocalMap(name = name) else getDistributedMap(name = name)

    private fun getLocalLong(name: String): SharedAtomicLong =
        localLongs.computeIfAbsent(name) { SimpleSharedAtomicLong(AtomicLong()) }

    protected abstract fun getDistributedLong(name: String): SharedAtomicLong

    private fun getLocalLock(name: String): SharedLock =
        localLocks.computeIfAbsent(name) { SimpleSharedLock(wrapped = ReentrantLock()) }

    protected abstract fun getDistributedLock(name: String): SharedLock

    @Suppress("UNCHECKED_CAST")
    private fun <K : Any, V : Any> getLocalMap(name: String): SharedMap<K, V> =
        localMaps.computeIfAbsent(name) { SimpleSharedMap<K, V>() } as SharedMap<K, V>

    protected abstract fun <K : Any, V : Any> getDistributedMap(name: String): SharedMap<K, V>
}
