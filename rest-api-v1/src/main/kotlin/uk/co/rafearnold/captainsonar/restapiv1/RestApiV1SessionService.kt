package uk.co.rafearnold.captainsonar.restapiv1

import io.vertx.ext.web.RoutingContext

interface RestApiV1SessionService {

    fun getUserId(ctx: RoutingContext): String

    fun getGameId(ctx: RoutingContext): String?

    fun setGameId(ctx: RoutingContext, gameId: String)
}
