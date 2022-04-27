package uk.co.rafearnold.captainsonar.repository

import java.util.concurrent.TimeUnit

interface GameRepository {

    fun createGame(gameId: String, game: StoredGame, ttl: Long, ttlUnit: TimeUnit): StoredGame

    fun loadGame(gameId: String): StoredGame?

    fun updateGame(
        gameId: String,
        updateOperations: Iterable<UpdateStoredGameOperation>,
        ttl: Long,
        ttlUnit: TimeUnit
    ): StoredGame

    fun deleteGame(gameId: String): StoredGame?

    fun gameExists(gameId: String): Boolean
}
