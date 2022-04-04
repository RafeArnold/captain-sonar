package uk.co.rafearnold.captainsonar.http

import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.common.Register
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class LoggingHandlerRouteRegister @Inject private constructor(
    private val router: Router
) : Register {

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            router.route()
                .handler {
                    logRequest(it)
                    it.next()
                }
        }

    private fun logRequest(ctx: RoutingContext) {
        val message: String =
            "Request received -" +
                    " method: '${ctx.request().method()}'," +
                    " path: '${ctx.request().path()}'," +
                    " headers: '${ctx.request().headers().joinToString { "${it.key}: ${it.value}" }}'," +
                    " query: '${ctx.request().query()}'," +
                    " body: '${ctx.bodyAsString}'"
        log.debug(message)
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(LoggingHandlerRouteRegister::class.java)
    }
}
