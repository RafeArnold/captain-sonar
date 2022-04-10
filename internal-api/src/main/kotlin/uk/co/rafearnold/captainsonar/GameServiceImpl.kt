package uk.co.rafearnold.captainsonar

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.common.GameAlreadyStartedException
import uk.co.rafearnold.captainsonar.common.NoSuchGameFoundException
import uk.co.rafearnold.captainsonar.common.PlayerAlreadyJoinedGameException
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.common.UserIsNotHostException
import uk.co.rafearnold.captainsonar.common.runAsync
import uk.co.rafearnold.captainsonar.eventapi.v1.EventApiV1Service
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEventEventApiV1Model
import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.GameEvent
import uk.co.rafearnold.captainsonar.model.Player
import uk.co.rafearnold.captainsonar.model.factory.GameEventFactory
import uk.co.rafearnold.captainsonar.model.factory.GameFactory
import uk.co.rafearnold.captainsonar.model.factory.PlayerFactory
import uk.co.rafearnold.captainsonar.model.mapper.ModelMapper
import uk.co.rafearnold.captainsonar.repository.AddPlayerOperation
import uk.co.rafearnold.captainsonar.repository.GameRepository
import uk.co.rafearnold.captainsonar.repository.SetStartedOperation
import uk.co.rafearnold.captainsonar.repository.StoredGame
import uk.co.rafearnold.captainsonar.repository.StoredPlayer
import uk.co.rafearnold.captainsonar.repository.UpdateStoredGameOperation
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import uk.co.rafearnold.captainsonar.shareddata.SharedLock
import uk.co.rafearnold.captainsonar.shareddata.getDistributedLock
import uk.co.rafearnold.captainsonar.shareddata.withLock
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameServiceImpl @Inject constructor(
    private val gameRepository: GameRepository,
    private val gameFactory: GameFactory,
    private val playerFactory: PlayerFactory,
    private val gameEventFactory: GameEventFactory,
    private val eventApiService: EventApiV1Service,
    sharedDataService: SharedDataService,
    private val modelMapper: ModelMapper
) : GameService, Register {

    private val lock: SharedLock =
        sharedDataService.getDistributedLock("uk.co.rafearnold.captainsonar.game-service.lock")

    private val gameEventListeners: MutableMap<String, MutableMap<String, GameListener>> = ConcurrentHashMap()

    private val listenerExecutor: Executor = Executors.newCachedThreadPool()

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            log.trace("Subscribing to game events")
            eventApiService.subscribeToGameEvents { event: GameEventEventApiV1Model ->
                val (gameId: String, gameEvent: GameEvent) = modelMapper.mapToGameEventPair(event = event)
                sendEventToListeners(gameId = gameId, event = gameEvent)
            }
        }

    override fun getGame(gameId: String): Game? {
        val storedGame: StoredGame? = gameRepository.loadGame(gameId = gameId)
        return storedGame?.let { modelMapper.mapToGame(gameId = gameId, storedGame = it) }
    }

    override fun createGame(hostId: String, hostName: String): Game =
        lock.withLock {
            val gameId: String = UUID.randomUUID().toString()
            val host: Player = playerFactory.create(name = hostName)
            val players: Map<String, Player> = mapOf(hostId to host)
            val game: Game =
                gameFactory.create(id = gameId, hostId = hostId, players = players, started = false)
            val storedGame: StoredGame =
                gameRepository.createGame(gameId = gameId, game = modelMapper.mapToStoredGame(game = game))
            modelMapper.mapToGame(gameId = gameId, storedGame = storedGame)
        }

    override fun addPlayer(gameId: String, playerId: String, playerName: String): Game =
        lock.withLock {
            val storedGame: StoredGame = loadGameOrThrow(gameId = gameId)
            if (storedGame.players.containsKey(playerId))
                throw PlayerAlreadyJoinedGameException(gameId = gameId, playerId = playerId, playerName = playerName)
            val updateOperations: List<UpdateStoredGameOperation> =
                listOf(AddPlayerOperation(playerId = playerId, player = StoredPlayer(name = playerName)))
            val updatedGame: StoredGame =
                gameRepository.updateGame(gameId = gameId, updateOperations = updateOperations)
            val game: Game = modelMapper.mapToGame(gameId = gameId, storedGame = updatedGame)
            publishEvent(gameId = gameId, event = gameEventFactory.createPlayerAddedEvent(game = game))
            return game
        }

    override fun startGame(gameId: String, playerId: String): Game =
        lock.withLock {
            val storedGame: StoredGame = loadGameAndConfirmHost(gameId = gameId, playerId = playerId)
            if (storedGame.started) throw GameAlreadyStartedException(gameId = gameId)
            val updateOperations: List<UpdateStoredGameOperation> = listOf(SetStartedOperation(started = true))
            val updatedGame: StoredGame =
                gameRepository.updateGame(gameId = gameId, updateOperations = updateOperations)
            val game: Game = modelMapper.mapToGame(gameId = gameId, storedGame = updatedGame)
            publishEvent(gameId = gameId, event = gameEventFactory.createGameStartedEvent(game = game))
            return game
        }

    override fun deleteGame(gameId: String, playerId: String) {
        lock.withLock {
            loadGameAndConfirmHost(gameId = gameId, playerId = playerId)
            gameRepository.deleteGame(gameId = gameId)
            publishEvent(gameId = gameId, event = gameEventFactory.createGameDeletedEvent())
            gameEventListeners.remove(gameId)
        }
    }

    override fun addGameListener(gameId: String, listener: GameListener): String {
        val listenerId: String = UUID.randomUUID().toString()
        gameEventListeners.computeIfAbsent(gameId) { ConcurrentHashMap() }[listenerId] = listener
        return listenerId
    }

    override fun removeGameListener(gameId: String, listenerId: String) {
        gameEventListeners[gameId]?.remove(listenerId)
    }

    private fun loadGameAndConfirmHost(gameId: String, playerId: String): StoredGame {
        val storedGame: StoredGame = loadGameOrThrow(gameId = gameId)
        if (storedGame.hostId != playerId) throw UserIsNotHostException(gameId = gameId, playerId = playerId)
        return storedGame
    }

    private fun loadGameOrThrow(gameId: String): StoredGame =
        gameRepository.loadGame(gameId = gameId) ?: throw NoSuchGameFoundException(gameId = gameId)

    private fun publishEvent(gameId: String, event: GameEvent) {
        val model: GameEventEventApiV1Model =
            modelMapper.mapToGameEventEventApiV1Model(gameId = gameId, event = event)
        eventApiService.publishGameEvent(event = model)
    }

    private fun sendEventToListeners(gameId: String, event: GameEvent) {
        val listeners: MutableMap<String, GameListener> = gameEventListeners[gameId] ?: return
        for ((listenerId: String, listener: GameListener) in listeners) {
            runAsync(listenerExecutor) {
                log.debug("Listener '$listenerId' handling event '$event'")
                listener.handle(event)
                log.debug("Listener '$listenerId' successfully handled event '$event'")
            }
                .exceptionally { log.error("Listener '$listenerId' failed to handle event '$event'"); null }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GameServiceImpl::class.java)
    }
}
