package uk.co.rafearnold.captainsonar.eventapi.v1.rabbitmq

import com.rabbitmq.client.AMQP
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.Channel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.eventapi.v1.EventApiV1Service
import uk.co.rafearnold.captainsonar.eventapi.v1.GameEventEventApiV1Handler
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.rabbitmq.model.codec.RabbitmqGameEventEventApiV1ModelCodec
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RabbitmqEventApiV1Service @Inject constructor(
    private val channel: Channel,
    private val codec: RabbitmqGameEventEventApiV1ModelCodec,
    private val consumerFactory: RabbitmqGameEventEventApiV1ConsumerFactory,
) : EventApiV1Service, Register {

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            log.trace("Declaring exchange")
            channel.exchangeDeclare(gameEventExchangeName, BuiltinExchangeType.FANOUT, true)
            log.trace("Exchange declared")
        }

    override fun publishGameEvent(event: GameEventEventApiV1Model) {
        channel.basicPublish(
            gameEventExchangeName,
            gameEventExchangeName,
            AMQP.BasicProperties(),
            codec.encode(event = event)
        )
    }

    override fun subscribeToGameEvents(handler: GameEventEventApiV1Handler) {
        val queueName: String = channel.queueDeclare().queue
        channel.queueBind(queueName, gameEventExchangeName, gameEventExchangeName)
        channel.basicConsume(queueName, true, consumerFactory.create(eventHandler = handler))
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RabbitmqEventApiV1Service::class.java)

        private const val gameEventExchangeName =
            "uk.co.rafearnold.captainsonar.event-api.v1.rabbitmq.exchange.game-event"
    }
}
