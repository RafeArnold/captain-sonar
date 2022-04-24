package uk.co.rafearnold.captainsonar.common

import io.vertx.core.Future
import java.util.concurrent.CompletableFuture

fun <T> Future<T>.toCompletableFuture(): CompletableFuture<T> {
    val completableFuture: CompletableFuture<T> = CompletableFuture()
    this.onComplete {
        if (it.succeeded()) completableFuture.complete(it.result())
        else completableFuture.completeExceptionally(it.cause())
    }
    return completableFuture
}
