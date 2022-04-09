package uk.co.rafearnold.captainsonar.event.model

import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeName("player-added")
data class PlayerAddedEventEventApiV1Model(
    override val gameId: String,
    val game: GameEventApiV1Model
) : GameEventEventApiV1Model
