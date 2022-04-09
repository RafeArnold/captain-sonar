package uk.co.rafearnold.captainsonar.restapiv1.handler

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import uk.co.rafearnold.captainsonar.common.IllegalGameStateException
import uk.co.rafearnold.captainsonar.restapiv1.RestApiV1Exception
import uk.co.rafearnold.captainsonar.restapiv1.RestApiV1ExceptionMapper
import javax.inject.Inject

class RestApiV1FailureHandler @Inject constructor(
    private val exceptionMapper: RestApiV1ExceptionMapper
) : Handler<RoutingContext> {

    override fun handle(ctx: RoutingContext) {
        val cause: Throwable = ctx.failure()
        val statusCode: Int
        val message: String
        when (cause) {
            is RestApiV1Exception -> {
                statusCode = cause.statusCode
                message = cause.message
            }
            is IllegalGameStateException -> {
                val apiException: RestApiV1Exception = exceptionMapper.mapToRestApiV1Exception(exception = cause)
                statusCode = apiException.statusCode
                message = apiException.message
            }
            else -> {
                statusCode = HttpResponseStatus.INTERNAL_SERVER_ERROR.code()
                message = "Unknown"
            }
        }
        ctx.response()
            .putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
            .setStatusCode(statusCode)
            .end(
                JsonObject()
                    .put("errorMessage", message)
                    .encode()
            )
    }
}
