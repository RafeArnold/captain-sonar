package uk.co.rafearnold.captainsonar.restapiv1

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.ext.web.Session
import uk.co.rafearnold.captainsonar.GameService
import uk.co.rafearnold.captainsonar.common.NoSuchGameFoundException
import uk.co.rafearnold.captainsonar.common.PlayerAlreadyJoinedGameException
import uk.co.rafearnold.captainsonar.common.Subscription
import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.restapiv1.model.CreateGameRequestRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.CreateGameResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.GameStateRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.GetGameStateResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.JoinGameRequestRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.JoinGameResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.StartGameResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.mapper.RestApiV1ModelMapper
import uk.co.rafearnold.commons.shareddata.SharedDataService
import uk.co.rafearnold.commons.shareddata.SharedLock
import uk.co.rafearnold.commons.shareddata.getDistributedLock
import uk.co.rafearnold.commons.shareddata.withLock
import javax.inject.Inject

class RestApiV1ServiceImpl @Inject constructor(
    private val gameService: GameService,
    private val sessionService: RestApiV1SessionService,
    sharedDataService: SharedDataService,
    private val modelMapper: RestApiV1ModelMapper
) : RestApiV1Service {

    private val lock: SharedLock =
        sharedDataService.getDistributedLock(name = "uk.co.rafearnold.captainsonar.rest-api-v1.service.lock")

    override fun getGameState(userId: String, gameId: String?, session: Session): GetGameStateResponseRestApiV1Model =
        if (gameId == null) {
            GetGameStateResponseRestApiV1Model(gameState = null)
        } else {
            val game: Game? = gameService.getGame(gameId = gameId)
            if (game == null) {
                sessionService.removeGameId(session = session)
                GetGameStateResponseRestApiV1Model(gameState = null)
            } else {
                val gameStateModel: GameStateRestApiV1Model =
                    modelMapper.mapToGameStateRestApiV1Model(game = game, userId = userId)
                GetGameStateResponseRestApiV1Model(gameState = gameStateModel)
            }
        }

    override fun createGame(
        userId: String,
        gameId: String?,
        request: CreateGameRequestRestApiV1Model,
        session: Session
    ): CreateGameResponseRestApiV1Model =
        lock.withLock {
            gameId.ensureGameIdIsNull()
            val game: Game = gameService.createGame(hostId = userId, hostName = request.hostName)
            sessionService.setGameId(session = session, gameId = game.id)
            CreateGameResponseRestApiV1Model(
                gameState = modelMapper.mapToGameStateRestApiV1Model(game = game, userId = userId)
            )
        }

    override fun joinGame(
        userId: String,
        gameId: String?,
        request: JoinGameRequestRestApiV1Model,
        session: Session
    ): JoinGameResponseRestApiV1Model =
        lock.withLock {
            gameId.ensureGameIdIsNull()
            val game: Game =
                try {
                    gameService.addPlayer(gameId = request.gameId, playerId = userId, playerName = request.playerName)
                } catch (e: PlayerAlreadyJoinedGameException) {
                    gameService.getGame(gameId = e.gameId)
                        ?: throw NoSuchGameFoundException(gameId = e.gameId)
                    // TODO: Rename the player to the requested name.
                }
            sessionService.setGameId(session = session, gameId = game.id)
            JoinGameResponseRestApiV1Model(
                gameState = modelMapper.mapToGameStateRestApiV1Model(game = game, userId = userId)
            )
        }

    override fun startGame(userId: String, gameId: String?, session: Session): StartGameResponseRestApiV1Model =
        lock.withLock {
            val game: Game =
                try {
                    gameService.startGame(gameId = gameId.ensureGameIdIsNonNull(), playerId = userId)
                } catch (e: NoSuchGameFoundException) {
                    sessionService.removeGameId(session = session)
                    throw e
                }
            StartGameResponseRestApiV1Model(
                gameState = modelMapper.mapToGameStateRestApiV1Model(game = game, userId = userId)
            )
        }

    override fun endGame(userId: String, gameId: String?, session: Session) {
        lock.withLock {
            try {
                gameService.endGame(gameId = gameId.ensureGameIdIsNonNull(), playerId = userId)
            } catch (e: NoSuchGameFoundException) {
                sessionService.removeGameId(session = session)
                throw e
            }
        }
    }

    override fun streamGame(
        userId: String,
        gameId: String?,
        session: Session,
        listener: RestApiV1GameListener
    ): Subscription =
        try {
            gameService.addGameListener(gameId.ensureGameIdIsNonNull()) {
                listener.handle(event = modelMapper.mapToGameEventRestApiV1Model(event = it, userId = userId))
            }
        } catch (e: NoSuchGameFoundException) {
            sessionService.removeGameId(session = session)
            throw e
        }

    private fun String?.ensureGameIdIsNull() {
        if (this != null)
            throw RestApiV1Exception(
                statusCode = HttpResponseStatus.CONFLICT.code(),
                message = "User is already in a game"
            )
    }

    private fun String?.ensureGameIdIsNonNull(): String =
        this
            ?: throw RestApiV1Exception(
                statusCode = HttpResponseStatus.CONFLICT.code(),
                message = "User is not in a game"
            )
}
