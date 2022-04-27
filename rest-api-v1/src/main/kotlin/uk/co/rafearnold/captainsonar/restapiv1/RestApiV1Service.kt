package uk.co.rafearnold.captainsonar.restapiv1

import io.vertx.ext.web.Session
import uk.co.rafearnold.captainsonar.common.Subscription
import uk.co.rafearnold.captainsonar.restapiv1.model.CreateGameRequestRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.CreateGameResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.GetGameStateResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.JoinGameRequestRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.JoinGameResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.StartGameResponseRestApiV1Model

interface RestApiV1Service {

    fun getGameState(userId: String, gameId: String?, session: Session): GetGameStateResponseRestApiV1Model

    fun createGame(
        userId: String,
        gameId: String?,
        request: CreateGameRequestRestApiV1Model,
        session: Session,
    ): CreateGameResponseRestApiV1Model

    fun joinGame(
        userId: String,
        gameId: String?,
        request: JoinGameRequestRestApiV1Model,
        session: Session,
    ): JoinGameResponseRestApiV1Model

    fun startGame(userId: String, gameId: String?, session: Session): StartGameResponseRestApiV1Model

    fun endGame(userId: String, gameId: String?, session: Session)

    fun streamGame(userId: String, gameId: String?, session: Session, listener: RestApiV1GameListener): Subscription
}
