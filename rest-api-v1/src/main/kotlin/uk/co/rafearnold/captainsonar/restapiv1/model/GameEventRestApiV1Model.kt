package uk.co.rafearnold.captainsonar.restapiv1.model

sealed interface GameEventRestApiV1Model {
    val eventType: String
}

object GameDeletedEventRestApiV1Model : GameEventRestApiV1Model {
    override val eventType: String = "game-deleted"
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
