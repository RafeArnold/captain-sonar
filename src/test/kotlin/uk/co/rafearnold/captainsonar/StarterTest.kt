package uk.co.rafearnold.captainsonar

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
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

        val deploymentConfig: JsonObject =
            JsonObject()
                .put("repository.type", "shared-data")
                .put("event-api.v1.type", "vertx")
                .put("game.id-generator.in-memory-random.random-seed", "5793545335685672764")
                .put("front-end.dir", "front-end/dist")
        val deploymentOptions1: DeploymentOptions =
            DeploymentOptions().setConfig(deploymentConfig.copy().put("server.port", "8180"))
        vertx1.deployVerticle(Starter(), deploymentOptions1).toCompletableFuture().get(30, TimeUnit.SECONDS)
        val deploymentOptions2: DeploymentOptions =
            DeploymentOptions().setConfig(deploymentConfig.copy().put("server.port", "8181"))
        vertx2.deployVerticle(Starter(), deploymentOptions2).toCompletableFuture().get(30, TimeUnit.SECONDS)
        val deploymentOptions3: DeploymentOptions =
            DeploymentOptions().setConfig(deploymentConfig.copy().put("server.port", "8182"))
        vertx3.deployVerticle(Starter(), deploymentOptions3).toCompletableFuture().get(30, TimeUnit.SECONDS)

        vertx1.close()
        vertx2.close()
        vertx3.close()
    }
}
