package uk.co.rafearnold.captainsonar.eventapi.v1.rabbitmq

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.Channel
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.Consumer
import com.rabbitmq.client.DefaultConsumer
import com.rabbitmq.client.Envelope
import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.RabbitMQContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameDeletedEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameStartedEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.PlayerAddedEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.PlayerEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.rabbitmq.model.codec.RabbitmqGameEventEventApiV1ModelCodec
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Testcontainers
class RabbitmqEventApiV1ServiceTest {

    @Container
    private val rabbitmqContainer: RabbitMQContainer =
        RabbitMQContainer(DockerImageName.parse("rabbitmq").withTag("alpine"))

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `events are published to all subscriptions`() {
        val connectionFactory = ConnectionFactory()
        connectionFactory.host = rabbitmqContainer.host
        connectionFactory.port = rabbitmqContainer.amqpPort
        val channel: Channel = connectionFactory.newConnection().createChannel()
        val codec = RabbitmqGameEventEventApiV1ModelCodec(jacksonObjectMapper())
        val consumerFactory = RabbitmqGameEventEventApiV1ConsumerFactoryImpl(codec = codec, channel = channel)
        val eventService =
            RabbitmqEventApiV1Service(channel = channel, codec = codec, consumerFactory = consumerFactory)

        eventService.register().get(2, TimeUnit.SECONDS)

        val consumer1Messages: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val consumer1: Consumer =
            object : DefaultConsumer(channel) {
                override fun handleDelivery(
                    consumerTag: String?,
                    envelope: Envelope?,
                    properties: AMQP.BasicProperties?,
                    body: ByteArray
                ) {
                    consumer1Messages.add(String(body, Charsets.UTF_8))
                }
            }
        val queueName1: String = channel.queueDeclare().queue
        channel.queueBind(
            queueName1,
            "uk.co.rafearnold.captainsonar.event-api.v1.rabbitmq.exchange.game-event",
            UUID.randomUUID().toString()
        )
        channel.basicConsume(queueName1, true, consumer1)

        val consumer2Messages: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val consumer2: Consumer =
            object : DefaultConsumer(channel) {
                override fun handleDelivery(
                    consumerTag: String?,
                    envelope: Envelope?,
                    properties: AMQP.BasicProperties?,
                    body: ByteArray
                ) {
                    consumer2Messages.add(String(body, Charsets.UTF_8))
                }
            }
        val queueName2: String = channel.queueDeclare().queue
        channel.queueBind(
            queueName2,
            "uk.co.rafearnold.captainsonar.event-api.v1.rabbitmq.exchange.game-event",
            UUID.randomUUID().toString()
        )
        channel.basicConsume(queueName2, true, consumer2)

        val event1: GameEventEventApiV1Model =
            PlayerAddedEventEventApiV1Model(
                gameId = "test_gameId1",
                game = GameEventApiV1Model(
                    id = "test_gameId2",
                    hostId = "test_hostId1",
                    players = mapOf(
                        "test_playerId1" to PlayerEventApiV1Model(id = "test_playerId2", name = "test_playerName1"),
                        "test_playerId3" to PlayerEventApiV1Model(id = "test_playerId3", name = "test_playerName2")
                    ),
                    started = false
                )
            )
        eventService.publishGameEvent(event = event1)
        val expectedSerializedEvent1 =
            """{"event-type":"player-added","gameId":"test_gameId1","game":{"id":"test_gameId2","hostId":"test_hostId1","players":{"test_playerId1":{"id":"test_playerId2","name":"test_playerName1"},"test_playerId3":{"id":"test_playerId3","name":"test_playerName2"}},"started":false}}"""

        val expectedConsumer1Messages1: Set<String> = setOf(expectedSerializedEvent1)
        CompletableFuture.runAsync { while (consumer1Messages.size != expectedConsumer1Messages1.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedConsumer1Messages1, consumer1Messages)
        val expectedConsumer2Messages1: Set<String> = setOf(expectedSerializedEvent1)
        CompletableFuture.runAsync { while (consumer2Messages.size != expectedConsumer2Messages1.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedConsumer2Messages1, consumer2Messages)

        val subscription1Events: MutableSet<GameEventEventApiV1Model> = ConcurrentHashMap.newKeySet()
        eventService.subscribeToGameEvents { subscription1Events.add(it) }

        val event2: GameEventEventApiV1Model =
            GameStartedEventEventApiV1Model(
                gameId = "test_gameId3",
                game = GameEventApiV1Model(
                    id = "test_gameId3",
                    hostId = "test_hostId2",
                    players = mapOf(
                        "test_playerId4" to PlayerEventApiV1Model(id = "test_playerId5", name = "test_playerName3"),
                        "test_playerId5" to PlayerEventApiV1Model(id = "test_playerId3", name = "test_playerName4")
                    ),
                    started = true
                )
            )
        eventService.publishGameEvent(event = event2)
        val expectedSerializedEvent2 =
            """{"event-type":"game-started","gameId":"test_gameId3","game":{"id":"test_gameId3","hostId":"test_hostId2","players":{"test_playerId4":{"id":"test_playerId5","name":"test_playerName3"},"test_playerId5":{"id":"test_playerId3","name":"test_playerName4"}},"started":true}}"""

        val expectedConsumer1Messages2: Set<String> = setOf(expectedSerializedEvent1, expectedSerializedEvent2)
        CompletableFuture.runAsync { while (consumer1Messages.size != expectedConsumer1Messages2.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedConsumer1Messages2, consumer1Messages)
        val expectedConsumer2Messages2: Set<String> = setOf(expectedSerializedEvent1, expectedSerializedEvent2)
        CompletableFuture.runAsync { while (consumer2Messages.size != expectedConsumer2Messages2.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedConsumer2Messages2, consumer2Messages)
        val expectedSubscription1Events1: Set<GameEventEventApiV1Model> = setOf(event2)
        CompletableFuture.runAsync { while (subscription1Events.size != expectedSubscription1Events1.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedSubscription1Events1, subscription1Events)

        val subscription2Events: MutableSet<GameEventEventApiV1Model> = ConcurrentHashMap.newKeySet()
        eventService.subscribeToGameEvents { subscription2Events.add(it) }

        val event3: GameEventEventApiV1Model = GameDeletedEventEventApiV1Model(gameId = "test_gameId4")
        eventService.publishGameEvent(event = event3)
        val expectedSerializedEvent3 = """{"event-type":"game-deleted","gameId":"test_gameId4"}"""

        val expectedConsumer1Messages3: Set<String> =
            setOf(expectedSerializedEvent1, expectedSerializedEvent2, expectedSerializedEvent3)
        CompletableFuture.runAsync { while (consumer1Messages.size != expectedConsumer1Messages3.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedConsumer1Messages3, consumer1Messages)
        val expectedConsumer2Messages3: Set<String> =
            setOf(expectedSerializedEvent1, expectedSerializedEvent2, expectedSerializedEvent3)
        CompletableFuture.runAsync { while (consumer2Messages.size != expectedConsumer2Messages3.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedConsumer2Messages3, consumer2Messages)
        val expectedSubscription1Events2: Set<GameEventEventApiV1Model> = setOf(event2, event3)
        CompletableFuture.runAsync { while (subscription1Events.size != expectedSubscription1Events2.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedSubscription1Events2, subscription1Events)
        val expectedSubscription2Events1: Set<GameEventEventApiV1Model> = setOf(event3)
        CompletableFuture.runAsync { while (subscription2Events.size != expectedSubscription2Events1.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedSubscription2Events1, subscription2Events)
    }
}
