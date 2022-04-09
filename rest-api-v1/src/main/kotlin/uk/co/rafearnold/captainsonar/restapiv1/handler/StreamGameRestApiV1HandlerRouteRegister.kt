package uk.co.rafearnold.captainsonar.restapiv1.handler

import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.vertx.core.Handler
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.restapiv1.RestApiV1Service
import uk.co.rafearnold.captainsonar.restapiv1.RestApiV1SessionService
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class StreamGameRestApiV1HandlerRouteRegister @Inject constructor(
    private val router: Router,
    private val apiService: RestApiV1Service,
    private val objectMapper: ObjectMapper,
    private val sessionService: RestApiV1SessionService,
    private val failureHandler: RestApiV1FailureHandler
) : Register, Handler<RoutingContext> {

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            router.get("/v1/game/stream")
                .handler(this)
                .failureHandler(failureHandler)
        }

    override fun handle(ctx: RoutingContext) {
        val userId: String = sessionService.getUserId(ctx)
        val gameId: String? = sessionService.getGameId(ctx)
        val response: HttpServerResponse =
            ctx.response()
                .setChunked(true)
                .putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_EVENT_STREAM)
                .putHeader(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
                .putHeader(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE)
        val streamId: String =
            apiService.streamGame(userId = userId, gameId = gameId) {
                val data: String = objectMapper.writeValueAsString(it)
                response.write("data: $data\n\n")
            }
        response.endHandler { apiService.endStream(streamId = streamId, gameId = gameId) }
    }
}
