package uk.co.rafearnold.captainsonar.repository

import com.fasterxml.jackson.databind.ObjectMapper
import redis.clients.jedis.Jedis
import redis.clients.jedis.Response
import redis.clients.jedis.Transaction
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import kotlin.concurrent.withLock

class RedisGameRepository @Inject constructor(
    private val redisClientProvider: RedisClientProvider,
    private val objectMapper: ObjectMapper
) : GameRepository {

    private val redisClient: Jedis get() = redisClientProvider.get()

    private val lock: Lock = ReentrantLock()

    override fun createGame(gameId: String, game: StoredGame): StoredGame =
        lock.withLock {
            val alreadyExists: Boolean = redisClient.setnx(gameIdKey(gameId = gameId), game.serialize()) == 0L
            if (alreadyExists) throw GameAlreadyExistsException(gameId = gameId)
            return game
        }

    override fun loadGame(gameId: String): StoredGame? =
        lock.withLock { redisClient.get(gameIdKey(gameId = gameId)).deserializeStoredGame() }

    override fun updateGame(gameId: String, updateOperations: Iterable<UpdateStoredGameOperation>): StoredGame =
        lock.withLock {
            val initialGame: StoredGame = loadGame(gameId = gameId) ?: throw NoSuchGameFoundException(gameId = gameId)
            val updatedGame: StoredGame =
                updateOperations.fold(initialGame) { game: StoredGame, operation: UpdateStoredGameOperation ->
                    operation.update(game)
                }
            redisClient.set(gameIdKey(gameId = gameId), updatedGame.serialize())
            updatedGame
        }

    override fun deleteGame(gameId: String): StoredGame? =
        lock.withLock {
            redisClient.multi().use { transaction: Transaction ->
                val gameIdKey: String = gameIdKey(gameId = gameId)
                val getResponse: Response<String?> = transaction.get(gameIdKey)
                transaction.del(gameIdKey)
                transaction.exec()
                getResponse.get()?.deserializeStoredGame()
            }
        }

    private fun StoredGame.serialize(): String = objectMapper.writeValueAsString(this)

    private fun String?.deserializeStoredGame() =
        this?.let { objectMapper.readValue(it, StoredGame::class.java) }

    companion object {
        private const val gameIdKeyPrefix = "uk.co.rafearnold.captainsonar.game."
        private fun gameIdKey(gameId: String): String = "$gameIdKeyPrefix$gameId"
    }
}
