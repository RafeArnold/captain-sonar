package uk.co.rafearnold.captainsonar.repository

data class StoredGame(
    val hostId: String,
    val players: Map<String, StoredPlayer>,
    val started: Boolean
)
