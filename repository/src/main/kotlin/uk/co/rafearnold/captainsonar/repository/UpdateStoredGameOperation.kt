package uk.co.rafearnold.captainsonar.repository

fun interface UpdateStoredGameOperation {
    fun update(storedGame: StoredGame): StoredGame
}
