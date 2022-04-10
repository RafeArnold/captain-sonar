package uk.co.rafearnold.captainsonar.eventapi.v1.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.eventapi.v1.GameEventEventApiV1Handler
import uk.co.rafearnold.captainsonar.eventapi.v1.rabbitmq.model.codec.RabbitmqGameEventEventApiV1ModelCodec

internal class RabbitmqGameEventEventApiV1Consumer(
    private val codec: RabbitmqGameEventEventApiV1ModelCodec,
    private val eventHandler: GameEventEventApiV1Handler,
    channel: Channel,
) : DefaultConsumer(channel) {

    override fun handleDelivery(
        consumerTag: String?,
        envelope: Envelope?,
        properties: AMQP.BasicProperties?,
        body: ByteArray
    ) {
        runCatching {
            eventHandler.handle(codec.decode(byteArray = body))
            log.trace("Event handled: ${String(body, Charsets.UTF_8)}")
        }.onFailure { log.error("Failure occurred while handling event: ${String(body, Charsets.UTF_8)}", it) }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RabbitmqGameEventEventApiV1Consumer::class.java)
    }
}
