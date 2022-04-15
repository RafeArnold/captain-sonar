package uk.co.rafearnold.captainsonar.eventapi.v1.vertx

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import io.vertx.core.Vertx
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEndedEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameStartedEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.PlayerAddedEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.PlayerEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.vertx.model.codec.VertxGameEventEventApiV1ModelCodec
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class VertxEventApiV1ServiceTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `events are published to all subscriptions`() {
        val vertx: Vertx = Vertx.vertx()
        val eventService = VertxEventApiV1Service(vertx = vertx)

        val codec = VertxGameEventEventApiV1ModelCodec(objectMapper = jacksonObjectMapper(), vertx = vertx)
        codec.register().get(2, TimeUnit.SECONDS)

        val consumer1Messages: MutableSet<GameEventEventApiV1Model> = ConcurrentHashMap.newKeySet()
        vertx.eventBus()
            .consumer<GameEventEventApiV1Model>("uk.co.rafearnold.captainsonar.event-api.v1.vertx.topic-address.game-event") {
                consumer1Messages.add(it.body())
            }

        val consumer2Messages: MutableSet<GameEventEventApiV1Model> = ConcurrentHashMap.newKeySet()
        vertx.eventBus()
            .consumer<GameEventEventApiV1Model>("uk.co.rafearnold.captainsonar.event-api.v1.vertx.topic-address.game-event") {
                consumer2Messages.add(it.body())
            }

        val event1: GameEventEventApiV1Model =
            PlayerAddedEventEventApiV1Model(
                gameId = "test_gameId1",
                game = GameEventApiV1Model(
                    hostId = "test_hostId1",
                    players = mapOf(
                        "test_playerId1" to PlayerEventApiV1Model(name = "test_playerName1"),
                        "test_playerId3" to PlayerEventApiV1Model(name = "test_playerName2"),
                    ),
                    started = false
                )
            )
        eventService.publishGameEvent(event = event1)

        val expectedConsumer1Messages1: Set<GameEventEventApiV1Model> = setOf(event1)
        CompletableFuture.runAsync { while (consumer1Messages.size != expectedConsumer1Messages1.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedConsumer1Messages1, consumer1Messages)
        val expectedConsumer2Messages1: Set<GameEventEventApiV1Model> = setOf(event1)
        CompletableFuture.runAsync { while (consumer2Messages.size != expectedConsumer2Messages1.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedConsumer2Messages1, consumer2Messages)

        val subscription1Events: MutableSet<GameEventEventApiV1Model> = ConcurrentHashMap.newKeySet()
        eventService.subscribeToGameEvents { subscription1Events.add(it) }

        val event2: GameEventEventApiV1Model =
            GameStartedEventEventApiV1Model(
                gameId = "test_gameId3",
                game = GameEventApiV1Model(
                    hostId = "test_hostId2",
                    players = mapOf(
                        "test_playerId4" to PlayerEventApiV1Model(name = "test_playerName3"),
                        "test_playerId5" to PlayerEventApiV1Model(name = "test_playerName4"),
                    ),
                    started = true
                )
            )
        eventService.publishGameEvent(event = event2)

        val expectedConsumer1Messages2: Set<GameEventEventApiV1Model> = setOf(event1, event2)
        CompletableFuture.runAsync { while (consumer1Messages.size != expectedConsumer1Messages2.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedConsumer1Messages2, consumer1Messages)
        val expectedConsumer2Messages2: Set<GameEventEventApiV1Model> = setOf(event1, event2)
        CompletableFuture.runAsync { while (consumer2Messages.size != expectedConsumer2Messages2.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedConsumer2Messages2, consumer2Messages)
        val expectedSubscription1Events1: Set<GameEventEventApiV1Model> = setOf(event2)
        CompletableFuture.runAsync { while (subscription1Events.size != expectedSubscription1Events1.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedSubscription1Events1, subscription1Events)

        val subscription2Events: MutableSet<GameEventEventApiV1Model> = ConcurrentHashMap.newKeySet()
        eventService.subscribeToGameEvents { subscription2Events.add(it) }

        val event3: GameEventEventApiV1Model = GameEndedEventEventApiV1Model(gameId = "test_gameId4")
        eventService.publishGameEvent(event = event3)

        val expectedConsumer1Messages3: Set<GameEventEventApiV1Model> = setOf(event1, event2, event3)
        CompletableFuture.runAsync { while (consumer1Messages.size != expectedConsumer1Messages3.size); }
            .get(5, TimeUnit.SECONDS)
        assertEquals(expectedConsumer1Messages3, consumer1Messages)
        val expectedConsumer2Messages3: Set<GameEventEventApiV1Model> = setOf(event1, event2, event3)
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

        vertx.close()
    }
}
