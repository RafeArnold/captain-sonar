package uk.co.rafearnold.captainsonar

import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import io.vertx.core.AbstractVerticle
import io.vertx.core.AsyncResult
import io.vertx.core.DeploymentOptions
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.Verticle
import io.vertx.core.json.JsonObject
import io.vertx.core.spi.VerticleFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.common.toCompletableFuture
import uk.co.rafearnold.captainsonar.config.ConfigRetrieverProvider
import uk.co.rafearnold.captainsonar.event.EventApiV1Module
import uk.co.rafearnold.captainsonar.guice.GuiceVerticleFactory
import uk.co.rafearnold.captainsonar.guice.MainModule
import uk.co.rafearnold.captainsonar.repository.RedisRepositoryModule
import uk.co.rafearnold.captainsonar.repository.SharedDataRepositoryModule
import uk.co.rafearnold.captainsonar.restapiv1.RestApiV1Module
import uk.co.rafearnold.captainsonar.shareddata.HazelcastSharedDataModule

class Starter : AbstractVerticle() {

    override fun start(startPromise: Promise<Void>) {
        ConfigRetrieverProvider(vertx = vertx).get()
            .config.toCompletableFuture()
            .thenAccept { initialConfig: JsonObject ->
                log.info("Initial config: $initialConfig")
                val injector: Injector =
                    Guice.createInjector(
                        MainModule(vertx = vertx, initialConfig = initialConfig),
                        HazelcastSharedDataModule(),
                        getRepositoryModule(config = initialConfig),
                        InternalApiModule(),
                        RestApiV1Module(),
                        EventApiV1Module(),
                    )
                val verticleFactory: VerticleFactory = injector.getInstance(GuiceVerticleFactory::class.java)
                vertx.registerVerticleFactory(verticleFactory)
                val verticlesToDeploy: List<Class<out Verticle>> = listOf(RegisterVerticle::class.java)
                var count = verticlesToDeploy.size
                val checkpoint: Handler<AsyncResult<String>> =
                    Handler { result ->
                        if (result.succeeded()) {
                            if (--count == 0) {
                                startPromise.complete()
                                log.info("Successfully launched starter verticle")
                            }
                        } else startPromise.fail(result.cause())
                    }
                for (verticleToDeploy: Class<out Verticle> in verticlesToDeploy) {
                    vertx.deployVerticle(
                        getVerticleDeploymentIdentifier(verticleToDeploy, verticleFactory),
                        DeploymentOptions().setWorker(true)
                    ) { outcome ->
                        if (outcome.failed()) {
                            log.error("Failed to start ${verticleToDeploy.simpleName}", outcome.cause())
                        } else log.info("Successfully deployed ${verticleToDeploy.simpleName}")
                        checkpoint.handle(outcome)
                    }
                }
            }
            .exceptionally { startPromise.fail(it); null }
    }

    private fun getRepositoryModule(config: JsonObject): Module =
        when (val repoType: String? = config.getString("repository.type")) {
            "redis" -> RedisRepositoryModule()
            "shared-data" -> SharedDataRepositoryModule()
            null -> throw IllegalArgumentException("No repository type provided")
            else -> throw IllegalArgumentException("Unrecognised repository type $repoType")
        }

    private fun getVerticleDeploymentIdentifier(
        verticleToDeploy: Class<out Verticle>,
        verticleFactory: VerticleFactory
    ) = "${verticleFactory.prefix()}:${verticleToDeploy.name}"

    companion object {
        private val log: Logger = LoggerFactory.getLogger(Starter::class.java)
    }
}
