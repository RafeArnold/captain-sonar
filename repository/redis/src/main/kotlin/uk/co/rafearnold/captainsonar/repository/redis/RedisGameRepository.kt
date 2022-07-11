package uk.co.rafearnold.captainsonar.repository.redis

import io.vertx.core.buffer.Buffer
import redis.clients.jedis.Jedis
import redis.clients.jedis.Response
import redis.clients.jedis.Transaction
import redis.clients.jedis.params.SetParams
import uk.co.rafearnold.captainsonar.common.GameAlreadyExistsException
import uk.co.rafearnold.captainsonar.common.NoSuchGameFoundException
import uk.co.rafearnold.captainsonar.repository.GameRepository
import uk.co.rafearnold.captainsonar.repository.StoredGame
import uk.co.rafearnold.captainsonar.repository.StoredGameSerializableHolder
import uk.co.rafearnold.captainsonar.repository.UpdateStoredGameOperation
import uk.co.rafearnold.commons.shareddata.SharedDataService
import uk.co.rafearnold.commons.shareddata.SharedLock
import uk.co.rafearnold.commons.shareddata.getDistributedLock
import uk.co.rafearnold.commons.shareddata.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class RedisGameRepository @Inject constructor(
    private val redisClientProvider: RedisClientProvider,
    sharedDataService: SharedDataService,
) : GameRepository {

    private val redisClient: Jedis get() = redisClientProvider.get()

    private val lock: SharedLock =
        sharedDataService.getDistributedLock("uk.co.rafearnold.captainsonar.repository.redis.lock")

    override fun createGame(gameId: String, game: StoredGame, ttl: Long, ttlUnit: TimeUnit): StoredGame =
        lock.withLock {
            val gameAlreadyExists: Boolean =
                redisClient.use {
                    it.multi().use { transaction: Transaction ->
                        val gameIdKey: ByteArray = gameIdKey(gameId = gameId)
                        val existsResponse: Response<Boolean> = transaction.exists(gameIdKey)
                        val setParams: SetParams = SetParams().nx().px(ttlUnit.toMillis(ttl))
                        transaction.set(gameIdKey, game.serialize(), setParams)
                        transaction.exec()
                        existsResponse.get()
                    }
                }
            if (gameAlreadyExists) throw GameAlreadyExistsException(gameId = gameId)
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
        lock.withLock { redisClient.use { it.getDel(gameIdKey(gameId = gameId))?.deserializeStoredGame() } }

    override fun gameExists(gameId: String): Boolean =
        lock.withLock { redisClient.exists(gameIdKey(gameId = gameId)) }

    private fun StoredGame.serialize(): ByteArray {
        val buffer: Buffer = Buffer.buffer()
        StoredGameSerializableHolder.create(this).writeToBuffer(buffer)
        return buffer.bytes
    }

    private fun ByteArray?.deserializeStoredGame(): StoredGame? =
        this?.let { StoredGameSerializableHolder.createFromBytes(this).storedGame }

    companion object {
        private const val gameIdKeyPrefix = "uk.co.rafearnold.captainsonar.game."
        private fun gameIdKey(gameId: String): ByteArray = "$gameIdKeyPrefix$gameId".toByteArray(Charsets.UTF_8)
    }
}
