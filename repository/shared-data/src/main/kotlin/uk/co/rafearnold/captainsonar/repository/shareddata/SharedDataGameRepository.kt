package uk.co.rafearnold.captainsonar.repository.shareddata

import com.fasterxml.jackson.databind.ObjectMapper
import uk.co.rafearnold.captainsonar.common.GameAlreadyExistsException
import uk.co.rafearnold.captainsonar.common.NoSuchGameFoundException
import uk.co.rafearnold.captainsonar.repository.GameRepository
import uk.co.rafearnold.captainsonar.repository.StoredGame
import uk.co.rafearnold.captainsonar.repository.UpdateStoredGameOperation
import uk.co.rafearnold.commons.shareddata.SharedDataService
import uk.co.rafearnold.commons.shareddata.SharedLock
import uk.co.rafearnold.commons.shareddata.SharedMap
import uk.co.rafearnold.commons.shareddata.getDistributedLock
import uk.co.rafearnold.commons.shareddata.getDistributedMap
import uk.co.rafearnold.commons.shareddata.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SharedDataGameRepository @Inject constructor(
    sharedDataService: SharedDataService,
    private val objectMapper: ObjectMapper
) : GameRepository {

    private val map: SharedMap<String, String> =
        sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")

    private val lock: SharedLock =
        sharedDataService.getDistributedLock("uk.co.rafearnold.captainsonar.repository.shared-data.lock")

    override fun createGame(gameId: String, game: StoredGame, ttl: Long, ttlUnit: TimeUnit): StoredGame =
        lock.withLock {
            val alreadyExists: Boolean =
                map.putIfAbsent(
                    key = gameIdKey(gameId = gameId),
                    value = game.serialize(),
                    ttl = ttl,
                    ttlUnit = ttlUnit
                ) != null
            if (alreadyExists) throw GameAlreadyExistsException(gameId = gameId)
            return game
        }

    override fun loadGame(gameId: String): StoredGame? =
        lock.withLock { map[gameIdKey(gameId = gameId)]?.deserializeStoredGame() }

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
            map.put(key = gameIdKey(gameId = gameId), value = updatedGame.serialize(), ttl = ttl, ttlUnit = ttlUnit)
            updatedGame
        }

    override fun deleteGame(gameId: String): StoredGame? =
        lock.withLock { map.remove(gameIdKey(gameId = gameId))?.deserializeStoredGame() }

    override fun gameExists(gameId: String): Boolean = lock.withLock { map.containsKey(gameIdKey(gameId = gameId)) }

    private fun StoredGame.serialize(): String = objectMapper.writeValueAsString(this)

    private fun String.deserializeStoredGame(): StoredGame? =
        this.let { objectMapper.readValue(it, StoredGame::class.java) }

    companion object {
        private const val gameIdKeyPrefix = "uk.co.rafearnold.captainsonar.game."
        private fun gameIdKey(gameId: String): String = "$gameIdKeyPrefix$gameId"
    }
}
