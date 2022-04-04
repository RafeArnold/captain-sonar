package uk.co.rafearnold.captainsonar.restapiv1

import io.netty.handler.codec.http.HttpResponseStatus
import io.vertx.ext.web.RoutingContext
import uk.co.rafearnold.captainsonar.GameService
import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.restapiv1.model.CreateGameRequestRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.CreateGameResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.JoinGameRequestRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.JoinGameResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.StartGameResponseRestApiV1Model
import uk.co.rafearnold.captainsonar.restapiv1.model.mapper.RestApiV1ModelMapper
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import kotlin.concurrent.withLock

class RestApiV1ServiceImpl @Inject constructor(
    private val gameService: GameService,
    private val sessionService: RestApiV1SessionService,
    private val modelMapper: RestApiV1ModelMapper
) : RestApiV1Service {

    private val lock: Lock = ReentrantLock()

    override fun createGame(
        userId: String,
        request: CreateGameRequestRestApiV1Model,
        ctx: RoutingContext
    ): CreateGameResponseRestApiV1Model =
        lock.withLock {
            if (sessionService.getGameId(ctx) != null)
                throw RestApiV1Exception(
                    statusCode = HttpResponseStatus.CONFLICT.code(),
                    message = "User is already in a game"
                )
            val game: Game = gameService.createGame(hostId = userId, hostName = request.hostName)
            sessionService.setGameId(ctx = ctx, gameId = game.id)
            CreateGameResponseRestApiV1Model(
                gameState = modelMapper.mapToGameStateRestApiV1Model(game = game, userId = userId)
            )
        }

    override fun joinGame(
        userId: String,
        request: JoinGameRequestRestApiV1Model,
        ctx: RoutingContext
    ): JoinGameResponseRestApiV1Model =
        lock.withLock {
            if (sessionService.getGameId(ctx) != null)
                throw RestApiV1Exception(
                    statusCode = HttpResponseStatus.CONFLICT.code(),
                    message = "User is already in a game"
                )
            val game: Game =
                gameService.addPlayer(gameId = request.gameId, playerId = userId, playerName = request.playerName)
            sessionService.setGameId(ctx = ctx, gameId = game.id)
            JoinGameResponseRestApiV1Model(
                gameState = modelMapper.mapToGameStateRestApiV1Model(game = game, userId = userId)
            )
        }

    override fun startGame(userId: String, ctx: RoutingContext): StartGameResponseRestApiV1Model =
        StartGameResponseRestApiV1Model(
            gameState = modelMapper.mapToGameStateRestApiV1Model(
                game = gameService.startGame(gameId = sessionService.getGameId(), playerId = userId),
                userId = userId
            )
        )

    override fun endGame(userId: String, ctx: RoutingContext) =
        gameService.deleteGame(gameId = gameId, playerId = userId)

    override fun streamGame(userId: String, gameId: String, listener: RestApiV1GameListener): String =
        gameService.addGameListener(gameId) {
            listener.handle(event = modelMapper.mapToGameEventRestApiV1Model(event = it, userId = userId))
        }

    override fun endStream(gameId: String, streamId: String) {
        gameService.removeGameListener(gameId = gameId, listenerId = streamId)
    }
}
