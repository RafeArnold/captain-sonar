package uk.co.rafearnold.captainsonar.restapiv1.handler

import com.fasterxml.jackson.databind.ObjectMapper
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.restapiv1.RestApiV1Service
import uk.co.rafearnold.captainsonar.restapiv1.RestApiV1SessionService
import uk.co.rafearnold.captainsonar.restapiv1.model.JoinGameRequestRestApiV1Model
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class JoinGameRestApiV1HandlerRouteRegister @Inject constructor(
    private val router: Router,
    private val apiService: RestApiV1Service,
    override val objectMapper: ObjectMapper,
    override val sessionService: RestApiV1SessionService,
    private val failureHandler: RestApiV1FailureHandler
) : Register, AbstractRestApiV1Handler() {

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            router.post("/v1/game/join")
                .handler(this)
                .failureHandler(failureHandler)
        }

    override fun handle(ctx: RoutingContext, userId: String): Any {
        val request: JoinGameRequestRestApiV1Model = ctx.readRequestBody()
        return apiService.joinGame(userId = userId, request = request, ctx = ctx)
    }
}
