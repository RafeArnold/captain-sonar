package uk.co.rafearnold.captainsonar

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import uk.co.rafearnold.captainsonar.common.Register
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class RegisterVerticle @Inject constructor(
    private val registers: Set<@JvmSuppressWildcards Register>
) : AbstractVerticle() {

    override fun start(startPromise: Promise<Void>) {
        // Registers are registered in sequence.
        registers.fold(CompletableFuture.completedFuture(null)) { acc: CompletableFuture<Void>, register: Register ->
            acc.thenCompose { register.register() }
        }.handle { _, e: Throwable? ->
            if (e != null) startPromise.fail(e)
            else startPromise.complete()
        }
    }
}
