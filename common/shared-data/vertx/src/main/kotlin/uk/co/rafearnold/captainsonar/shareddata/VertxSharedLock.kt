package uk.co.rafearnold.captainsonar.shareddata

import io.vertx.core.shareddata.Lock
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Supplier

class VertxSharedLock(
    private val lockSupplier: Supplier<Lock>,
    private val lockTtlSupplier: Function<Long, Lock>
) : SharedLock {

    private var currentLock: Lock? = null

    override fun lock() {
        currentLock = lockSupplier.get()
    }

    override fun lock(ttl: Long, ttlUnit: TimeUnit): Boolean {
        currentLock =
            try {
                lockTtlSupplier.apply(ttlUnit.toMillis(ttl))
            } catch (e: Exception) {
                return false
            }
        return true
    }

    override fun unlock() {
        currentLock?.release()
        currentLock = null
    }
}
