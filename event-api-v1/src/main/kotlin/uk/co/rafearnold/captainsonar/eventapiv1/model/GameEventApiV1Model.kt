package uk.co.rafearnold.captainsonar.eventapiv1.model

data class GameEventApiV1Model(
    val id: String,
    val hostId: String,
    val players: Map<String, PlayerEventApiV1Model>,
    val started: Boolean
)
