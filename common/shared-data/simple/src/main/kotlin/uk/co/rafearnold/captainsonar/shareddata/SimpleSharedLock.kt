package uk.co.rafearnold.captainsonar.shareddata

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class SimpleSharedLock(private val wrapped: ReentrantLock) : SharedLock, Lock by wrapped {

    override fun lock(ttl: Long, ttlUnit: TimeUnit): Boolean = wrapped.tryLock(ttl, ttlUnit)
}
