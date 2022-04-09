package uk.co.rafearnold.captainsonar.restapiv1.model

sealed interface GameEventRestApiV1Model {
    val eventType: String
}

object GameEndedEventRestApiV1Model : GameEventRestApiV1Model {
    override val eventType: String = "game-ended"
}

data class GameStartedEventRestApiV1Model(
    val gameState: GameStateRestApiV1Model
) : GameEventRestApiV1Model {
    override val eventType: String = "game-started"
}

data class PlayerJoinedEventRestApiV1Model(
    val gameState: GameStateRestApiV1Model
) : GameEventRestApiV1Model {
    override val eventType: String = "player-joined"
}
