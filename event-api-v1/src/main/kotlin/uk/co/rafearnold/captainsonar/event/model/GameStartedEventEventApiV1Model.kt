package uk.co.rafearnold.captainsonar.event.model

import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeName("game-started")
data class GameStartedEventEventApiV1Model(
    override val gameId: String,
    val game: GameEventApiV1Model
) : GameEventEventApiV1Model
