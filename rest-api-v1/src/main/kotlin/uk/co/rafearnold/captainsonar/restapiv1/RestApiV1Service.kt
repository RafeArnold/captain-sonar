package uk.co.rafearnold.captainsonar.restapiv1

import io.vertx.ext.web.RoutingContext
import uk.co.rafearnold.captainsonar.restapiv1.model.CreateGameRequestRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.CreateGameResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.JoinGameRequestRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.JoinGameResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.StartGameResponseRestApiV1Model

interface RestApiV1Service {

    fun createGame(
        userId: String,
        request: CreateGameRequestRestApiV1Model,
        ctx: RoutingContext
    ): CreateGameResponseRestApiV1Model

    fun joinGame(
        userId: String,
        request: JoinGameRequestRestApiV1Model,
        ctx: RoutingContext
    ): JoinGameResponseRestApiV1Model

    fun startGame(userId: String, ctx: RoutingContext): StartGameResponseRestApiV1Model

    fun endGame(userId: String, ctx: RoutingContext)

    fun streamGame(userId: String, gameId: String, listener: RestApiV1GameListener): String

    fun endStream(gameId: String, streamId: String)
}
