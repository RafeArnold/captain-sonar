package uk.co.rafearnold.captainsonar.shareddata

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.shareddata.AsyncMap
import io.vertx.core.shareddata.Counter
import io.vertx.core.shareddata.Lock
import uk.co.rafearnold.captainsonar.common.toCompletableFuture
import java.util.function.Function
import java.util.function.Supplier
import javax.inject.Inject

internal class VertxSharedDataService @Inject constructor(
    private val vertx: Vertx
) : SharedDataService {

    override fun getLong(name: String, localOnly: Boolean): SharedAtomicLong {
        val counterFuture: Future<Counter> =
            if (localOnly) vertx.sharedData().getLocalCounter(name)
            else vertx.sharedData().getCounter(name)
        return VertxSharedAtomicLong(counter = counterFuture.toCompletableFuture().get())
    }

    override fun getLock(name: String, localOnly: Boolean): SharedLock {
        val lockSupplier: Supplier<Lock> =
            if (localOnly) Supplier { vertx.sharedData().getLocalLock(name).toCompletableFuture().get() }
            else Supplier { vertx.sharedData().getLock(name).toCompletableFuture().get() }
        val lockTtlSupplier: Function<Long, Lock> =
            if (localOnly) Function { vertx.sharedData().getLocalLockWithTimeout(name, it).toCompletableFuture().get() }
            else Function { vertx.sharedData().getLockWithTimeout(name, it).toCompletableFuture().get() }
        return VertxSharedLock(lockSupplier = lockSupplier, lockTtlSupplier = lockTtlSupplier)
    }

    override fun <K : Any, V : Any> getMap(name: String, localOnly: Boolean): SharedMap<K, V> {
        val mapFuture: Future<AsyncMap<K, V>> =
            if (localOnly) vertx.sharedData().getLocalAsyncMap(name)
            else vertx.sharedData().getAsyncMap(name)
        val lock: SharedLock =
            getLock(name = "__uk.co.rafearnold.captainsonar.vertx.shared-map-locks.$name", localOnly = localOnly)
        return VertxSharedMap(asyncMap = mapFuture.toCompletableFuture().get(), lock = lock)
    }
}
