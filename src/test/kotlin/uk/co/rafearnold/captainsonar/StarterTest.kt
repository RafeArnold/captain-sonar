package uk.co.rafearnold.captainsonar

import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import org.junit.jupiter.api.Test
import uk.co.rafearnold.captainsonar.common.toCompletableFuture
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class StarterTest {

    @Test
    fun `starter starts`() {
        val vertx1Future: CompletableFuture<Vertx> = Vertx.clusteredVertx(VertxOptions()).toCompletableFuture()
        val vertx2Future: CompletableFuture<Vertx> = Vertx.clusteredVertx(VertxOptions()).toCompletableFuture()
        val vertx3Future: CompletableFuture<Vertx> = Vertx.clusteredVertx(VertxOptions()).toCompletableFuture()
        val vertx1: Vertx = vertx1Future.get(30, TimeUnit.SECONDS)
        val vertx2: Vertx = vertx2Future.get(30, TimeUnit.SECONDS)
        val vertx3: Vertx = vertx3Future.get(30, TimeUnit.SECONDS)

        System.setProperty("application.properties.path", "application.properties")

        System.setProperty("server.port", "8180")
        vertx1.deployVerticle(Starter()).toCompletableFuture().get(30, TimeUnit.SECONDS)
        System.setProperty("server.port", "8181")
        vertx2.deployVerticle(Starter()).toCompletableFuture().get(30, TimeUnit.SECONDS)
        System.setProperty("server.port", "8182")
        vertx3.deployVerticle(Starter()).toCompletableFuture().get(30, TimeUnit.SECONDS)

        vertx1.close()
        vertx2.close()
        vertx3.close()
    }
}
