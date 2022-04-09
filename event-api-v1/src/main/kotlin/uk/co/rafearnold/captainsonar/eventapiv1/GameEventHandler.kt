package uk.co.rafearnold.captainsonar.eventapiv1

import uk.co.rafearnold.captainsonar.eventapiv1.model.GameEventEventApiV1Model

fun interface GameEventHandler {
    fun handle(event: GameEventEventApiV1Model)
}
