package uk.co.rafearnold.captainsonar.shareddata.hazelcast

import com.hazelcast.core.HazelcastInstance
import uk.co.rafearnold.captainsonar.shareddata.simple.AbstractSharedDataService
import uk.co.rafearnold.captainsonar.shareddata.SharedAtomicLong
import uk.co.rafearnold.captainsonar.shareddata.SharedLock
import uk.co.rafearnold.captainsonar.shareddata.SharedMap
import javax.inject.Inject

class HazelcastSharedDataService @Inject constructor(
    private val hazelcastInstance: HazelcastInstance
) : AbstractSharedDataService() {

    override fun getDistributedLong(name: String): SharedAtomicLong =
        HazelcastSharedAtomicLong(hazelcastLong = hazelcastInstance.cpSubsystem.getAtomicLong(name))

    override fun getDistributedLock(name: String): SharedLock =
        HazelcastSharedLock(hazelcastLock = hazelcastInstance.cpSubsystem.getLock(name))

    override fun <K : Any, V : Any> getDistributedMap(name: String): SharedMap<K, V> =
        HazelcastSharedMap(hazelcastMap = hazelcastInstance.getMap(name))
}
