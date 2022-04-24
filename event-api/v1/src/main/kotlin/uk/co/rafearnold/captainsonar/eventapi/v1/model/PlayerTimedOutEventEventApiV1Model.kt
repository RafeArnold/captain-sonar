package uk.co.rafearnold.captainsonar.eventapi.v1.model

import com.fasterxml.jackson.annotation.JsonTypeName

@JsonTypeName("player-timed-out")
data class PlayerTimedOutEventEventApiV1Model(
    override val gameId: String,
    val game: GameEventApiV1Model
) : GameEventEventApiV1Model
