package uk.co.rafearnold.captainsonar.shareddata.simple

import uk.co.rafearnold.captainsonar.shareddata.SharedAtomicLong
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import uk.co.rafearnold.captainsonar.shareddata.SharedLock
import uk.co.rafearnold.captainsonar.shareddata.SharedMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

/**
 * A singleton to manage all virtual clusters.
 */
object SimpleClusterManager {

    /**
     * Each instantiated cluster mapped to its unique ID.
     */
    private val clusters: MutableMap<String, SimpleCluster> = ConcurrentHashMap()

    /**
     * Retrieves the virtual cluster associated with the provided [clusterId]. If no cluster with
     * the provided [clusterId] has been instantiated, then a new one is created and returned.
     */
    private fun getCluster(clusterId: String): SimpleCluster = clusters.computeIfAbsent(clusterId) { SimpleCluster() }

    /**
     * Provides a [SharedDataService] backed by the cluster associated with the given [clusterId].
     * All [SharedDataService] retrieved using the same [clusterId] will share the same data.
     *
     * @param clusterId The ID of the virtual cluster that the returned [SharedDataService] will
     * use. Each ID corresponds to a unique cluster.
     */
    fun createSharedDataService(clusterId: String): SharedDataService =
        SimpleSharedDataService(cluster = getCluster(clusterId = clusterId))

    fun clearAllClusters() {
        clusters.clear()
    }

    /**
     * A simple implementation of a virtual cluster. Similar to [AbstractSharedDataService].
     */
    private class SimpleCluster {

        private val longs: MutableMap<String, SharedAtomicLong> = ConcurrentHashMap()

        private val locks: MutableMap<String, SharedLock> = ConcurrentHashMap()

        private val maps: MutableMap<String, SharedMap<*, *>> = ConcurrentHashMap()

        fun getLong(name: String): SharedAtomicLong =
            longs.computeIfAbsent(name) { SimpleSharedAtomicLong(wrappedLong = AtomicLong()) }

        fun getLock(name: String): SharedLock = locks.computeIfAbsent(name) { SimpleSharedLock(ReentrantLock()) }

        @Suppress("UNCHECKED_CAST")
        fun <K, V : Any> getMap(name: String): SharedMap<K, V> =
            maps.computeIfAbsent(name) { SimpleSharedMap<K, V>() } as SharedMap<K, V>
    }

    /**
     * Simple extension of [AbstractSharedDataService] that uses virtual clusters to simulate
     * distributed data. These virtual clusters are managed by [SimpleClusterManager].
     */
    private class SimpleSharedDataService(
        private val cluster: SimpleCluster
    ) : AbstractSharedDataService() {

        override fun getDistributedLong(name: String): SharedAtomicLong = cluster.getLong(name = name)

        override fun getDistributedLock(name: String): SharedLock = cluster.getLock(name = name)

        override fun <K : Any, V : Any> getDistributedMap(name: String): SharedMap<K, V> = cluster.getMap(name = name)
    }
}
