package uk.co.rafearnold.captainsonar.http

import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.sstore.SessionStore
import uk.co.rafearnold.captainsonar.common.Register
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class SessionHandlerRouteRegister @Inject constructor(
    private val vertx: Vertx,
    private val router: Router
) : Register {

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            val handler: SessionHandler =
                SessionHandler.create(SessionStore.create(vertx))
                    .setSessionCookieName("uk.co.rafearnold.captainsonar.session")
                    .setCookieHttpOnlyFlag(true)
                    .setCookieSecureFlag(true)
            router.route().handler(handler)
        }
}
