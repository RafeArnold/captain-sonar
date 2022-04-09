package uk.co.rafearnold.captainsonar.restapiv1

import io.vertx.ext.web.RoutingContext

class RestApiV1SessionServiceImpl : RestApiV1SessionService {

    override fun getUserId(ctx: RoutingContext): String = ctx.session().id()

    override fun getGameId(ctx: RoutingContext): String? = ctx.session().get(gameIdKey)

    override fun setGameId(ctx: RoutingContext, gameId: String) {
        ctx.session().put(gameIdKey, gameId)
    }

    override fun removeGameId(ctx: RoutingContext) {
        ctx.session().remove<Any>(gameIdKey)
    }

    companion object {
        private const val gameIdKey = "uk.co.rafearnold.captainsonar.game-id"
    }
}
