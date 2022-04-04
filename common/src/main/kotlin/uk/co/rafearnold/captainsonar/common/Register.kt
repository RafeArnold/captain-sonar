package uk.co.rafearnold.captainsonar.common

import java.util.concurrent.CompletableFuture

interface Register {
    fun register(): CompletableFuture<Void>
}
