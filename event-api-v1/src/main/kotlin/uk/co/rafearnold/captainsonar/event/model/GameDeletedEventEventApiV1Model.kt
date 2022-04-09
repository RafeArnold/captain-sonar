package uk.co.rafearnold.captainsonar.event.model

import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeName("game-deleted")
data class GameDeletedEventEventApiV1Model(
    override val gameId: String
) : GameEventEventApiV1Model
