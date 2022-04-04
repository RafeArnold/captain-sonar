package uk.co.rafearnold.captainsonar.http

import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import uk.co.rafearnold.captainsonar.common.Register
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class BodyHandlerRouteRegister @Inject private constructor(
    private val router: Router
) : Register {

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync { router.route().handler(BodyHandler.create()) }
}
