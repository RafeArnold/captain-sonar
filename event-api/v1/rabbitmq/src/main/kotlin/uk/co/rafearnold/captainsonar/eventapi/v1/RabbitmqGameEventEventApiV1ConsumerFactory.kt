package uk.co.rafearnold.captainsonar.eventapi.v1

internal interface RabbitmqGameEventEventApiV1ConsumerFactory {

    fun create(eventHandler: GameEventEventApiV1Handler): RabbitmqGameEventEventApiV1Consumer
}
