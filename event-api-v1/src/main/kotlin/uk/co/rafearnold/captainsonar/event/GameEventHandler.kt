package uk.co.rafearnold.captainsonar.event

import uk.co.rafearnold.captainsonar.event.model.GameEventEventApiV1Model

fun interface GameEventHandler {
    fun handle(event: GameEventEventApiV1Model)
}
