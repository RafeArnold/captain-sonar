package uk.co.rafearnold.captainsonar.common

import io.vertx.core.Future
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

fun <T> Future<T>.toCompletableFuture(): CompletableFuture<T> {
    val completableFuture: CompletableFuture<T> = CompletableFuture()
    this.onComplete {
        if (it.succeeded()) completableFuture.complete(it.result())
        else completableFuture.completeExceptionally(it.cause())
    }
    return completableFuture
}

fun runAsync(executor: Executor, runnable: Runnable): CompletableFuture<Void> =
    CompletableFuture.runAsync(runnable, executor)
