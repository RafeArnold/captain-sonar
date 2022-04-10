package uk.co.rafearnold.captainsonar.eventapi.v1.rabbitmq

import uk.co.rafearnold.captainsonar.eventapi.v1.GameEventEventApiV1Handler

internal interface RabbitmqGameEventEventApiV1ConsumerFactory {

    fun create(eventHandler: GameEventEventApiV1Handler): RabbitmqGameEventEventApiV1Consumer
}
