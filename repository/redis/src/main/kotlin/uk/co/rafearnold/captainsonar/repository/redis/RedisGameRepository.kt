package uk.co.rafearnold.captainsonar.repository.redis

import com.fasterxml.jackson.databind.ObjectMapper
import redis.clients.jedis.Jedis
import redis.clients.jedis.Response
import redis.clients.jedis.Transaction
import redis.clients.jedis.params.SetParams
import uk.co.rafearnold.captainsonar.common.GameAlreadyExistsException
import uk.co.rafearnold.captainsonar.common.NoSuchGameFoundException
import uk.co.rafearnold.captainsonar.repository.GameRepository
import uk.co.rafearnold.captainsonar.repository.StoredGame
import uk.co.rafearnold.captainsonar.repository.UpdateStoredGameOperation
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import uk.co.rafearnold.captainsonar.shareddata.SharedLock
import uk.co.rafearnold.captainsonar.shareddata.getDistributedLock
import uk.co.rafearnold.captainsonar.shareddata.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class RedisGameRepository @Inject constructor(
    private val redisClientProvider: RedisClientProvider,
    sharedDataService: SharedDataService,
    private val objectMapper: ObjectMapper
) : GameRepository {

    private val redisClient: Jedis get() = redisClientProvider.get()

    private val lock: SharedLock =
        sharedDataService.getDistributedLock("uk.co.rafearnold.captainsonar.repository.redis.lock")

    override fun createGame(gameId: String, game: StoredGame, ttl: Long, ttlUnit: TimeUnit): StoredGame =
        lock.withLock {
            val existingValue: String? =
                redisClient.use {
                    it.multi().use { transaction: Transaction ->
                        val gameIdKey: String = gameIdKey(gameId = gameId)
                        val getResponse: Response<String> = transaction.get(gameIdKey)
                        val setParams: SetParams = SetParams().nx().px(ttlUnit.toMillis(ttl))
                        transaction.set(gameIdKey, game.serialize(), setParams)
                        transaction.exec()
                        getResponse.get()
                    }
                }
            if (existingValue != null) throw GameAlreadyExistsException(gameId = gameId)
            return game
        }

    override fun loadGame(gameId: String): StoredGame? =
        lock.withLock { redisClient.use { it.get(gameIdKey(gameId = gameId)) }.deserializeStoredGame() }

    override fun updateGame(
        gameId: String,
        updateOperations: Iterable<UpdateStoredGameOperation>,
        ttl: Long,
        ttlUnit: TimeUnit
    ): StoredGame =
        lock.withLock {
            val initialGame: StoredGame = loadGame(gameId = gameId) ?: throw NoSuchGameFoundException(gameId = gameId)
            val updatedGame: StoredGame =
                updateOperations.fold(initialGame) { game: StoredGame, operation: UpdateStoredGameOperation ->
                    operation.update(game)
                }
            val setParams: SetParams = SetParams().px(ttlUnit.toMillis(ttl))
            redisClient.use { it.set(gameIdKey(gameId = gameId), updatedGame.serialize(), setParams) }
            updatedGame
        }

    override fun deleteGame(gameId: String): StoredGame? =
        lock.withLock {
            redisClient.use {
                it.multi().use { transaction: Transaction ->
                    val gameIdKey: String = gameIdKey(gameId = gameId)
                    val getResponse: Response<String?> = transaction.get(gameIdKey)
                    transaction.del(gameIdKey)
                    transaction.exec()
                    getResponse.get()?.deserializeStoredGame()
                }
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
