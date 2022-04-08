package uk.co.rafearnold.captainsonar.shareddata

/**
 * An API for retrieving shared data, either shared within the current [SharedDataService] instance
 * (locally) or across a cluster of applications (distributed). The definition of a cluster is
 * specific to the implementation.
 */
interface SharedDataService {

    /**
     * Retrieves the [SharedAtomicLong] associated with the given [name]. If no [SharedAtomicLong]
     * is associated with the given [name] already, then a new one will be created and returned
     * with an initial value of 0.
     *
     * @param localOnly Whether the data returned is local or from across the cluster.
     */
    fun getLong(name: String, localOnly: Boolean): SharedAtomicLong

    /**
     * Retrieves the [SharedLock] associated with the given [name]. If no [SharedLock] is
     * associated with the given [name] already, then a new (unlocked) one will be created and
     * returned.
     *
     * @param localOnly Whether the data returned is local or from across the cluster.
     */
    fun getLock(name: String, localOnly: Boolean): SharedLock

    /**
     * Retrieves the [SharedMap] associated with the given [name]. If no [SharedMap] is associated
     * with the given [name] already, then an empty one will be created and returned.
     *
     * @param localOnly Whether the data returned is local or from across the cluster.
     */
    fun <K : Any, V : Any> getMap(name: String, localOnly: Boolean): SharedMap<K, V>
}
