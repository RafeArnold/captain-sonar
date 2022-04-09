package uk.co.rafearnold.captainsonar.http

import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.SessionHandler
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.repository.session.SessionStoreFactory
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class SessionHandlerRouteRegister @Inject constructor(
    private val sessionStoreFactory: SessionStoreFactory,
    private val router: Router
) : Register {

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            val handler: SessionHandler =
                SessionHandler.create(sessionStoreFactory.create())
                    .setSessionCookieName("uk.co.rafearnold.captainsonar.session")
                    .setCookieHttpOnlyFlag(true)
                    .setCookieSecureFlag(true)
            router.route().handler(handler)
        }
}
