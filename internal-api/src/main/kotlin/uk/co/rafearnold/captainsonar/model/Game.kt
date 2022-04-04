package uk.co.rafearnold.captainsonar.model

interface Game {
    val id: String
    val hostId: String
    val players: Map<String, Player>
    val started: Boolean
}
