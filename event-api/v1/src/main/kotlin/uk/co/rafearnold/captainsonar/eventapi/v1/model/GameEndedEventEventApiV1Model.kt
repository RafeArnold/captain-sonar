package uk.co.rafearnold.captainsonar.eventapi.v1.model

import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeName("game-ended")
data class GameEndedEventEventApiV1Model(
    override val gameId: String
) : GameEventEventApiV1Model
