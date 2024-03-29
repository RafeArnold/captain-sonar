package uk.co.rafearnold.captainsonar.eventapi.v1.rabbitmq

import com.rabbitmq.client.Channel
import uk.co.rafearnold.captainsonar.eventapi.v1.GameEventEventApiV1Handler
import uk.co.rafearnold.captainsonar.eventapi.v1.rabbitmq.model.codec.RabbitmqGameEventEventApiV1ModelCodec
import javax.inject.Inject

internal class RabbitmqGameEventEventApiV1ConsumerFactoryImpl @Inject constructor(
    private val codec: RabbitmqGameEventEventApiV1ModelCodec,
    private val channel: Channel
) : RabbitmqGameEventEventApiV1ConsumerFactory {

    override fun create(eventHandler: GameEventEventApiV1Handler): RabbitmqGameEventEventApiV1Consumer =
        RabbitmqGameEventEventApiV1Consumer(codec = codec, eventHandler = eventHandler, channel = channel)
}
