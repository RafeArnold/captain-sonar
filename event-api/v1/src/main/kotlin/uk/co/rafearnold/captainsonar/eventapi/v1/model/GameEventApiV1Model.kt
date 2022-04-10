package uk.co.rafearnold.captainsonar.eventapi.v1.model

data class GameEventApiV1Model(
    val hostId: String,
    val players: Map<String, PlayerEventApiV1Model>,
    val started: Boolean
)
