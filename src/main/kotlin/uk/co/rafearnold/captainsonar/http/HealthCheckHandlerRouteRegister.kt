package uk.co.rafearnold.captainsonar.http

import io.vertx.core.Vertx
import io.vertx.ext.healthchecks.HealthCheckHandler
import io.vertx.ext.healthchecks.HealthChecks
import io.vertx.ext.healthchecks.Status
import io.vertx.ext.web.Router
import uk.co.rafearnold.captainsonar.common.Register
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class HealthCheckHandlerRouteRegister @Inject constructor(
    private val vertx: Vertx,
    private val router: Router
) : Register {

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            val healthChecks: HealthChecks = HealthChecks.create(vertx)
            healthChecks.register("application") { it.complete(Status.OK()) }
            router.get("/health")
                .handler(HealthCheckHandler.createWithHealthChecks(healthChecks))
        }
}
