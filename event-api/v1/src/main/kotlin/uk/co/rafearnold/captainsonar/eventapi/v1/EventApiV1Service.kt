package uk.co.rafearnold.captainsonar.eventapi.v1

import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEventEventApiV1Model

interface EventApiV1Service {

    fun publishGameEvent(event: GameEventEventApiV1Model)

    fun subscribeToGameEvents(handler: GameEventEventApiV1Handler)
}
