package uk.co.rafearnold.captainsonar

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.common.runAsync
import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.GameEvent
import uk.co.rafearnold.captainsonar.model.Player
import uk.co.rafearnold.captainsonar.model.factory.GameEventFactory
import uk.co.rafearnold.captainsonar.model.factory.GameFactory
import uk.co.rafearnold.captainsonar.model.factory.PlayerFactory
import uk.co.rafearnold.captainsonar.model.mapper.ModelMapper
import uk.co.rafearnold.captainsonar.repository.AddPlayerOperation
import uk.co.rafearnold.captainsonar.repository.GameAlreadyStartedException
import uk.co.rafearnold.captainsonar.repository.GameRepository
import uk.co.rafearnold.captainsonar.repository.NoSuchGameFoundException
import uk.co.rafearnold.captainsonar.repository.PlayerAlreadyJoinedGameException
import uk.co.rafearnold.captainsonar.repository.SetStartedOperation
import uk.co.rafearnold.captainsonar.repository.StoredGame
import uk.co.rafearnold.captainsonar.repository.StoredPlayer
import uk.co.rafearnold.captainsonar.repository.UpdateStoredGameOperation
import uk.co.rafearnold.captainsonar.repository.UserIsNotHostException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import kotlin.concurrent.withLock

class GameServiceImpl @Inject constructor(
    private val gameRepository: GameRepository,
    private val gameFactory: GameFactory,
    private val playerFactory: PlayerFactory,
    private val gameEventFactory: GameEventFactory,
    private val modelMapper: ModelMapper
) : GameService {

    private val lock: Lock = ReentrantLock()

    private val gameEventListeners: MutableMap<String, MutableMap<String, GameListener>> = ConcurrentHashMap()

    private val listenerExecutor: Executor = Executors.newCachedThreadPool()

    override fun createGame(hostId: String, hostName: String): Game =
        lock.withLock {
            val gameId: String = UUID.randomUUID().toString()
            val host: Player = playerFactory.create(id = hostId, name = hostName)
            val players: Map<String, Player> = mapOf(hostId to host)
            val game: Game =
                gameFactory.create(id = gameId, hostId = hostId, players = players, started = false)
            val storedGame: StoredGame =
                gameRepository.createGame(gameId = gameId, game = modelMapper.mapToStoredGame(game = game))
            modelMapper.mapToMutableGame(gameId = gameId, storedGame = storedGame)
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
            val game: Game = modelMapper.mapToMutableGame(gameId = gameId, storedGame = updatedGame)
            handleEvent(gameId = gameId, event = gameEventFactory.createPlayerAddedEvent(game = game))
            return game
        }

    override fun startGame(gameId: String, playerId: String): Game =
        lock.withLock {
            val storedGame: StoredGame = loadGameAndConfirmHost(gameId = gameId, playerId = playerId)
            if (storedGame.started) throw GameAlreadyStartedException(gameId = gameId)
            val updateOperations: List<UpdateStoredGameOperation> = listOf(SetStartedOperation(started = true))
            val updatedGame: StoredGame =
                gameRepository.updateGame(gameId = gameId, updateOperations = updateOperations)
            val game: Game = modelMapper.mapToMutableGame(gameId = gameId, storedGame = updatedGame)
            handleEvent(gameId = gameId, event = gameEventFactory.createGameStartedEvent(game = game))
            return game
        }

    override fun deleteGame(gameId: String, playerId: String) {
        lock.withLock {
            loadGameAndConfirmHost(gameId = gameId, playerId = playerId)
            gameRepository.deleteGame(gameId = gameId)
            gameEventListeners.remove(gameId)
            handleEvent(gameId = gameId, event = gameEventFactory.createGameDeletedEvent())
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

    private fun handleEvent(gameId: String, event: GameEvent) {
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
