package uk.co.rafearnold.captainsonar.repository

data class SetStartedOperation(
    private val started: Boolean
) : UpdateStoredGameOperation {
    override fun update(storedGame: StoredGame): StoredGame =
        storedGame.copy(started = started)
}
