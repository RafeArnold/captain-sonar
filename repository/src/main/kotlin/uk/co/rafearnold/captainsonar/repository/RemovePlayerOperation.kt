package uk.co.rafearnold.captainsonar.repository

data class RemovePlayerOperation(
    private val playerId: String,
) : UpdateStoredGameOperation {
    override fun update(storedGame: StoredGame): StoredGame =
        storedGame.copy(players = storedGame.players - playerId)
}
