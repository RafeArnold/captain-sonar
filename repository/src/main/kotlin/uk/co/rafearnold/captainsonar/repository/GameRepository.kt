package uk.co.rafearnold.captainsonar.repository

interface GameRepository {

    fun createGame(gameId: String, game: StoredGame): StoredGame

    fun loadGame(gameId: String): StoredGame?

    fun updateGame(gameId: String, updateOperations: Iterable<UpdateStoredGameOperation>): StoredGame

    fun deleteGame(gameId: String): StoredGame?
}
