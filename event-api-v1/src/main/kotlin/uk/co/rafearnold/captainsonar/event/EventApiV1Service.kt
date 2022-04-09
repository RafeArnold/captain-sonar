package uk.co.rafearnold.captainsonar.event

import uk.co.rafearnold.captainsonar.event.model.GameEventEventApiV1Model

interface EventApiV1Service {

    fun publishGameEvent(event: GameEventEventApiV1Model)

    fun subscribeToGameEvents(handler: GameEventHandler)
}
