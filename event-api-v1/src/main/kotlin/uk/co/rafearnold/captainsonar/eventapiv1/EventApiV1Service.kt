package uk.co.rafearnold.captainsonar.eventapiv1

import uk.co.rafearnold.captainsonar.eventapiv1.model.GameEventEventApiV1Model

interface EventApiV1Service {

    fun publishGameEvent(event: GameEventEventApiV1Model)

    fun subscribeToGameEvents(handler: GameEventHandler)
}
