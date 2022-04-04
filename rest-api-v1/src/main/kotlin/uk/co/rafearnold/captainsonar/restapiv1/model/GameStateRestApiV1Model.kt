package uk.co.rafearnold.captainsonar.restapiv1.model

data class GameStateRestApiV1Model(
    val id: String,
    val hosting: Boolean,
    val players: List<PlayerRestApiV1Model>,
    val started: Boolean
)
