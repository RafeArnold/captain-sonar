package uk.co.rafearnold.captainsonar.restapiv1.handler

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Handler
import io.vertx.ext.web.RoutingContext
import uk.co.rafearnold.captainsonar.restapiv1.RestApiV1Exception
import uk.co.rafearnold.captainsonar.restapiv1.RestApiV1SessionService

abstract class AbstractRestApiV1Handler : Handler<RoutingContext> {

    protected abstract val objectMapper: ObjectMapper

    protected abstract val sessionService: RestApiV1SessionService

    override fun handle(ctx: RoutingContext) {
        val userId: String = sessionService.getUserId(session = ctx.session())
        val gameId: String? = sessionService.getGameId(session = ctx.session())
        val response: Any? = handle(ctx = ctx, userId = userId, gameId = gameId)
        ctx.response()
            .setStatusCode(HttpResponseStatus.OK.code())
            .apply {
                if (response != null) {
                    putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                        .end(objectMapper.writeValueAsString(response))
                } else end()
            }
    }

    protected abstract fun handle(ctx: RoutingContext, userId: String, gameId: String?): Any?

    protected inline fun <reified T> RoutingContext.readRequestBody(): T =
        try {
            objectMapper.readValue(this.bodyAsString, object : TypeReference<T>() {})
        } catch (e: Exception) {
            throw RestApiV1Exception(
                statusCode = HttpResponseStatus.BAD_REQUEST.code(),
                message = "Failed to deserialize request body",
                cause = e
            )
        }
}
