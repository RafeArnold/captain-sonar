package uk.co.rafearnold.captainsonar.repository

data class AddPlayerOperation(
    private val playerId: String,
    private val player: StoredPlayer
) : UpdateStoredGameOperation {
    override fun update(storedGame: StoredGame): StoredGame =
        storedGame.copy(players = storedGame.players + (playerId to player))
}
