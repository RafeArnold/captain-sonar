package uk.co.rafearnold.captainsonar.shareddata.hazelcast

import com.hazelcast.cp.lock.FencedLock
import uk.co.rafearnold.captainsonar.shareddata.SharedLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class HazelcastSharedLock @Inject constructor(
    private val hazelcastLock: FencedLock
) : SharedLock {

    override fun lock() {
        hazelcastLock.lock()
    }

    override fun lock(ttl: Long, ttlUnit: TimeUnit): Boolean = hazelcastLock.tryLock(ttl, ttlUnit)

    override fun unlock() {
        hazelcastLock.unlock()
    }
}
