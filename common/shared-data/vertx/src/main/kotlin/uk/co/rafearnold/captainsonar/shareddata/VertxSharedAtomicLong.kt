package uk.co.rafearnold.captainsonar.shareddata

import io.vertx.core.shareddata.Counter
import uk.co.rafearnold.captainsonar.common.toCompletableFuture

internal class VertxSharedAtomicLong(private val counter: Counter) : SharedAtomicLong {

    override fun get(): Long = counter.get().toCompletableFuture().get()

    override fun compareAndSet(expectValue: Long, newValue: Long): Boolean =
        counter.compareAndSet(expectValue, newValue).toCompletableFuture().get()

    override fun getAndIncrement(): Long = counter.andIncrement.toCompletableFuture().get()
}
