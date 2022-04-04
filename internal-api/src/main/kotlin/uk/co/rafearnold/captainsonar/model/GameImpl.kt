package uk.co.rafearnold.captainsonar.model

data class GameImpl(
    override val id: String,
    override val hostId: String,
    override val players: Map<String, Player>,
    override val started: Boolean
) : Game
