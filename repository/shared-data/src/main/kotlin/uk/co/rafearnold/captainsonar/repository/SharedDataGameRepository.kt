package uk.co.rafearnold.captainsonar.repository

import com.fasterxml.jackson.databind.ObjectMapper
import uk.co.rafearnold.captainsonar.common.GameAlreadyExistsException
import uk.co.rafearnold.captainsonar.common.NoSuchGameFoundException
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import uk.co.rafearnold.captainsonar.shareddata.SharedLock
import uk.co.rafearnold.captainsonar.shareddata.SharedMap
import uk.co.rafearnold.captainsonar.shareddata.getDistributedLock
import uk.co.rafearnold.captainsonar.shareddata.getDistributedMap
import uk.co.rafearnold.captainsonar.shareddata.withLock
import javax.inject.Inject

class SharedDataGameRepository @Inject constructor(
    sharedDataService: SharedDataService,
    private val objectMapper: ObjectMapper
) : GameRepository {

    private val map: SharedMap<String, String> =
        sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")

    private val lock: SharedLock =
        sharedDataService.getDistributedLock("uk.co.rafearnold.captainsonar.repository.shared-data.lock")

    override fun createGame(gameId: String, game: StoredGame): StoredGame =
        lock.withLock {
            val alreadyExists: Boolean = map.putIfAbsent(gameIdKey(gameId = gameId), game.serialize()) != null
            if (alreadyExists) throw GameAlreadyExistsException(gameId = gameId)
            return game
        }

    override fun loadGame(gameId: String): StoredGame? =
        lock.withLock { map[gameIdKey(gameId = gameId)]?.deserializeStoredGame() }

    override fun updateGame(gameId: String, updateOperations: Iterable<UpdateStoredGameOperation>): StoredGame =
        lock.withLock {
            val initialGame: StoredGame = loadGame(gameId = gameId) ?: throw NoSuchGameFoundException(gameId = gameId)
            val updatedGame: StoredGame =
                updateOperations.fold(initialGame) { game: StoredGame, operation: UpdateStoredGameOperation ->
                    operation.update(game)
                }
            map[gameIdKey(gameId = gameId)] = updatedGame.serialize()
            updatedGame
        }

    override fun deleteGame(gameId: String): StoredGame? =
        lock.withLock { map.remove(gameIdKey(gameId = gameId))?.deserializeStoredGame() }

    private fun StoredGame.serialize(): String = objectMapper.writeValueAsString(this)

    private fun String.deserializeStoredGame(): StoredGame? =
        this.let { objectMapper.readValue(it, StoredGame::class.java) }

    companion object {
        private const val gameIdKeyPrefix = "uk.co.rafearnold.captainsonar.game."
        private fun gameIdKey(gameId: String): String = "$gameIdKeyPrefix$gameId"
    }
}
