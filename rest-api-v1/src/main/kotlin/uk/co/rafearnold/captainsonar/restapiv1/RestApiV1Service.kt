package uk.co.rafearnold.captainsonar.restapiv1

import io.vertx.ext.web.RoutingContext
import uk.co.rafearnold.captainsonar.restapiv1.model.CreateGameRequestRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.CreateGameResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.GetGameStateResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.JoinGameRequestRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.JoinGameResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.StartGameResponseRestApiV1Model

interface RestApiV1Service {

    fun getGameState(userId: String, gameId: String?, ctx: RoutingContext): GetGameStateResponseRestApiV1Model

    fun createGame(
        userId: String,
        gameId: String?,
        request: CreateGameRequestRestApiV1Model,
        ctx: RoutingContext
    ): CreateGameResponseRestApiV1Model

    fun joinGame(
        userId: String,
        gameId: String?,
        request: JoinGameRequestRestApiV1Model,
        ctx: RoutingContext
    ): JoinGameResponseRestApiV1Model

    fun startGame(userId: String, gameId: String?): StartGameResponseRestApiV1Model

    fun endGame(userId: String, gameId: String?)

    fun streamGame(userId: String, gameId: String?, listener: RestApiV1GameListener): String

    fun endStream(streamId: String, gameId: String?)
}
