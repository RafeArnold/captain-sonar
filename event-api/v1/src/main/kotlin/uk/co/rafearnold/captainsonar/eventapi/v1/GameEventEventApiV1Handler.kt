package uk.co.rafearnold.captainsonar.eventapi.v1

import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEventEventApiV1Model

fun interface GameEventEventApiV1Handler {
    fun handle(event: GameEventEventApiV1Model)
}
