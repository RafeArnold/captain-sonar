package uk.co.rafearnold.captainsonar.eventapi.v1

import com.rabbitmq.client.Channel
import uk.co.rafearnold.captainsonar.eventapi.v1.model.codec.RabbitmqGameEventEventApiV1ModelCodec
import javax.inject.Inject

internal class RabbitmqGameEventEventApiV1ConsumerFactoryImpl @Inject constructor(
    private val codec: RabbitmqGameEventEventApiV1ModelCodec,
    private val channel: Channel
) : RabbitmqGameEventEventApiV1ConsumerFactory {

    override fun create(eventHandler: GameEventEventApiV1Handler): RabbitmqGameEventEventApiV1Consumer =
        RabbitmqGameEventEventApiV1Consumer(codec = codec, eventHandler = eventHandler, channel = channel)
}
