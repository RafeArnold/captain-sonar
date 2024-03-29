package uk.co.rafearnold.captainsonar

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.common.GameAlreadyStartedException
import uk.co.rafearnold.captainsonar.common.NoSuchGameFoundException
import uk.co.rafearnold.captainsonar.common.NoSuchPlayerFoundException
import uk.co.rafearnold.captainsonar.common.PlayerAlreadyJoinedGameException
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.common.Subscription
import uk.co.rafearnold.captainsonar.common.UserIsNotHostException
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
import uk.co.rafearnold.captainsonar.repository.RemovePlayerOperation
import uk.co.rafearnold.captainsonar.repository.SetStartedOperation
import uk.co.rafearnold.captainsonar.repository.StoredGame
import uk.co.rafearnold.captainsonar.repository.StoredPlayer
import uk.co.rafearnold.captainsonar.repository.UpdateStoredGameOperation
import uk.co.rafearnold.commons.config.ObservableMap
import uk.co.rafearnold.commons.shareddata.SharedDataService
import uk.co.rafearnold.commons.shareddata.SharedLock
import uk.co.rafearnold.commons.shareddata.getDistributedLock
import uk.co.rafearnold.commons.shareddata.withLock
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

@Singleton
class GameServiceImpl @Inject constructor(
    private val gameRepository: GameRepository,
    private val gameFactory: GameFactory,
    private val playerFactory: PlayerFactory,
    private val gameEventFactory: GameEventFactory,
    private val eventApiService: EventApiV1Service,
    sharedDataService: SharedDataService,
    private val gameIdGenerator: GameIdGenerator,
    private val modelMapper: ModelMapper,
    private val appConfig: ObservableMap<String, String>,
) : GameService, Register {

    private val lock: SharedLock =
        sharedDataService.getDistributedLock("uk.co.rafearnold.captainsonar.game-service.lock")

    // A local lock since publishers are only relevant within a cluster member.
    private val publisherLock: Lock = ReentrantLock()
    private val submissionPublishers: MutableMap<String, SubmissionPublisher<GameEvent>> = ConcurrentHashMap()

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            log.trace("Subscribing to game events")
            eventApiService.subscribeToGameEvents { event: GameEventEventApiV1Model ->
                val (gameId: String, gameEvent: GameEvent) = modelMapper.mapToGameEventPair(event = event)
                log.debug("Game event received for game '$gameId': $event")
                publisherLock.withLock { submissionPublishers[gameId]?.submit(gameEvent) }
            }
        }

    override fun getGame(gameId: String): Game? {
        val storedGame: StoredGame? = gameRepository.loadGame(gameId = gameId)
        return storedGame?.let { modelMapper.mapToGame(gameId = gameId, storedGame = it) }
    }

    override fun createGame(hostId: String, hostName: String): Game =
        lock.withLock {
            val gameId: String = gameIdGenerator.generateId()
            val host: Player = playerFactory.create(name = hostName)
            val players: Map<String, Player> = mapOf(hostId to host)
            val game: Game =
                gameFactory.create(id = gameId, hostId = hostId, players = players, started = false)
            val storedGame: StoredGame =
                gameRepository.createGame(
                    gameId = gameId,
                    game = modelMapper.mapToStoredGame(game = game),
                    ttl = ttlMs,
                    ttlUnit = TimeUnit.MILLISECONDS,
                )
            modelMapper.mapToGame(gameId = gameId, storedGame = storedGame)
        }

    override fun addPlayer(gameId: String, playerId: String, playerName: String): Game =
        lock.withLock {
            val storedGame: StoredGame = loadGameOrThrow(gameId = gameId)
            if (storedGame.players.containsKey(playerId))
                throw PlayerAlreadyJoinedGameException(gameId = gameId, playerId = playerId, playerName = playerName)
            val updateOperations: List<UpdateStoredGameOperation> =
                listOf(AddPlayerOperation(playerId = playerId, player = StoredPlayer(name = playerName)))
            val updatedGame: StoredGame = updateGame(gameId = gameId, updateOperations = updateOperations)
            val game: Game = modelMapper.mapToGame(gameId = gameId, storedGame = updatedGame)
            publishEvent(gameId = gameId, event = gameEventFactory.createPlayerAddedEvent(game = game))
            return game
        }

    override fun timeoutPlayer(gameId: String, playerId: String): Game {
        lock.withLock {
            val storedGame: StoredGame = loadGameOrThrow(gameId = gameId)
            if (!storedGame.players.containsKey(playerId))
                throw NoSuchPlayerFoundException(gameId = gameId, playerId = playerId)
            val updateOperations: List<UpdateStoredGameOperation> = listOf(RemovePlayerOperation(playerId = playerId))
            val updatedGame: StoredGame = updateGame(gameId = gameId, updateOperations = updateOperations)
            val game: Game = modelMapper.mapToGame(gameId = gameId, storedGame = updatedGame)
            publishEvent(gameId = gameId, event = gameEventFactory.createPlayerTimedOutEvent(game = game))
            return game
        }
    }

    override fun startGame(gameId: String, playerId: String): Game =
        lock.withLock {
            val storedGame: StoredGame = loadGameAndConfirmHost(gameId = gameId, playerId = playerId)
            if (storedGame.started) throw GameAlreadyStartedException(gameId = gameId)
            val updateOperations: List<UpdateStoredGameOperation> = listOf(SetStartedOperation(started = true))
            val updatedGame: StoredGame = updateGame(gameId = gameId, updateOperations = updateOperations)
            val game: Game = modelMapper.mapToGame(gameId = gameId, storedGame = updatedGame)
            publishEvent(gameId = gameId, event = gameEventFactory.createGameStartedEvent(game = game))
            return game
        }

    override fun endGame(gameId: String, playerId: String) {
        lock.withLock {
            loadGameAndConfirmHost(gameId = gameId, playerId = playerId)
            gameRepository.deleteGame(gameId = gameId)
            publishEvent(gameId = gameId, event = gameEventFactory.createGameEndedEvent())
        }
    }

    override fun addGameListener(gameId: String, consumer: Consumer<GameEvent>): Subscription =
        lock.withLock {
            assertGameExists(gameId = gameId)
            val subscriptionFuture: CompletableFuture<Void> =
                publisherLock.withLock {
                    submissionPublishers.computeIfAbsent(gameId) { createPublisher(gameId = gameId) }.consume(consumer)
                }
            return Subscription {
                subscriptionFuture.complete(null)
                removeGameEventPublisherIfNoSubscribers(gameId = gameId)
            }
        }

    private fun assertGameExists(gameId: String) {
        if (!gameRepository.gameExists(gameId = gameId)) throw NoSuchGameFoundException(gameId = gameId)
    }

    private fun createPublisher(gameId: String): SubmissionPublisher<GameEvent> =
        SubmissionPublisher(Executors.newCachedThreadPool(), Flow.defaultBufferSize()) { _, throwable: Throwable ->
            log.error("Listener failed to handle event for game '$gameId'", throwable)
        }

    private fun removeGameEventPublisherIfNoSubscribers(gameId: String) {
        publisherLock.withLock {
            val publisher: SubmissionPublisher<GameEvent> = submissionPublishers[gameId] ?: return
            if (publisher.numberOfSubscribers == 0) {
                submissionPublishers.remove(gameId)
                publisher.close()
            }
        }
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

    private fun updateGame(gameId: String, updateOperations: List<UpdateStoredGameOperation>): StoredGame =
        gameRepository.updateGame(
            gameId = gameId,
            updateOperations = updateOperations,
            ttl = ttlMs,
            ttlUnit = TimeUnit.MILLISECONDS,
        )

    private val ttlMs: Long
        get() {
            val configuredTtlString: String? = appConfig["game.ttl.ms"]
            val ttl: Long =
                if (configuredTtlString != null) {
                    val configuredTtl: Long? = configuredTtlString.toLongOrNull()
                    if (configuredTtl == null) {
                        log.error("Configured game TTL '$configuredTtlString' is not a valid integer")
                        DEFAULT_GAME_TTL_MS
                    } else configuredTtl
                } else DEFAULT_GAME_TTL_MS
            return ttl
        }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GameServiceImpl::class.java)

        private const val DEFAULT_GAME_TTL_MS: Long = 60 * 60 * 1000 // 1 hour.
    }
}
