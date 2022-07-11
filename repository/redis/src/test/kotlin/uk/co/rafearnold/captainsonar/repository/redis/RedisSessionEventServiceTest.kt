package uk.co.rafearnold.captainsonar.repository.redis

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.vertx.core.Vertx
import io.vertx.ext.auth.VertxContextPRNG
import io.vertx.ext.web.sstore.SessionStore
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.SetParams
import uk.co.rafearnold.captainsonar.common.Subscription
import uk.co.rafearnold.captainsonar.repository.session.SessionEvent
import uk.co.rafearnold.captainsonar.repository.session.SessionExpiredEvent
import uk.co.rafearnold.commons.config.ObservableMutableMap
import uk.co.rafearnold.commons.config.ObservableMutableMapImpl
import java.util.Queue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

@Testcontainers
class RedisSessionEventServiceTest {

    companion object {
        @Container
        private val redisContainer1: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("redis").withTag("alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server --notify-keyspace-events KEA")

        private const val redisContainer2Password = "test_password"

        @Container
        private val redisContainer2: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("redis").withTag("alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server --notify-keyspace-events KEA --requirepass $redisContainer2Password")
    }

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
        Jedis(redisContainer1.host, redisContainer1.firstMappedPort).flushAll()
        Jedis(
            redisContainer2.host,
            redisContainer2.firstMappedPort,
            DefaultJedisClientConfig.builder().password(redisContainer2Password).build()
        ).flushAll()
    }

    @Test
    @Suppress("ControlFlowWithEmptyBody")
    fun `session events can be subscribed to and unsubscribed from`() {
        val vertx: Vertx = Vertx.vertx()

        val redisClientProvider: RedisClientProvider = mockk(relaxed = true)
        val sessionCodec: RedisSessionCodec = RedisSessionCodecImpl()
        val eventService =
            RedisSessionEventService(
                redisClientProvider = redisClientProvider,
                sessionCodec = sessionCodec,
            )

        val redisClientPool = JedisPool(redisContainer1.host, redisContainer1.firstMappedPort)
        every { redisClientProvider.get() } answers { redisClientPool.resource }

        val subscription1Events: Queue<SessionEvent> = ConcurrentLinkedQueue()
        val subscription1: Subscription = eventService.subscribeToSessionEvents { subscription1Events.add(it) }

        val session1 =
            SharedDataSessionImpl(VertxContextPRNG.current(vertx), 23, SessionStore.DEFAULT_SESSIONID_LENGTH)
        val session2 =
            SharedDataSessionImpl(VertxContextPRNG.current(vertx), 45, SessionStore.DEFAULT_SESSIONID_LENGTH)
        val ttlMs: Long = 10
        val redisClient = Jedis(redisContainer1.host, redisContainer1.firstMappedPort)
        redisClient.set(
            "uk.co.rafearnold.captainsonar.session.${session1.id()}".toByteArray(Charsets.UTF_8),
            sessionCodec.serialize(session1),
        )
        redisClient.set("uk.co.rafearnold.captainsonar.session-shadow.${session1.id()}", "", SetParams().px(ttlMs))
        redisClient.set(
            "uk.co.rafearnold.captainsonar.session.${session2.id()}".toByteArray(Charsets.UTF_8),
            sessionCodec.serialize(session2),
        )
        redisClient.set("uk.co.rafearnold.captainsonar.session-shadow.${session2.id()}", "")

        // Wait for the entry to expire.
        Thread.sleep(ttlMs)
        // Retrieve all keys from the db to trigger redis to acknowledge the entry that has expired.
        redisClient.keys("*")

        CompletableFuture.runAsync { while (subscription1Events.size != 1); }.get(2, TimeUnit.SECONDS)
        assertEquals(1, subscription1Events.size)
        val subscription1Event1: SessionExpiredEvent = subscription1Events.poll() as SessionExpiredEvent
        assertEquals(session1.id(), subscription1Event1.session.id())
        assertEquals(session1.timeout(), subscription1Event1.session.timeout())
        assertEquals(session1.lastAccessed(), subscription1Event1.session.lastAccessed())

        val subscription2Events: Queue<SessionEvent> = ConcurrentLinkedQueue()
        val subscription2: Subscription = eventService.subscribeToSessionEvents { subscription2Events.add(it) }

        redisClient.pexpire("uk.co.rafearnold.captainsonar.session-shadow.${session2.id()}", ttlMs)

        // Wait for the entry to expire.
        Thread.sleep(ttlMs)
        // Retrieve all keys from the db to trigger redis to acknowledge the entry that has expired.
        redisClient.keys("*")

        CompletableFuture.runAsync { while (subscription1Events.size != 1); }.get(2, TimeUnit.SECONDS)
        assertEquals(1, subscription1Events.size)
        val subscription1Event2: SessionExpiredEvent = subscription1Events.poll() as SessionExpiredEvent
        assertEquals(session2.id(), subscription1Event2.session.id())

        CompletableFuture.runAsync { while (subscription2Events.size != 1); }.get(2, TimeUnit.SECONDS)
        assertEquals(1, subscription2Events.size)
        val subscription2Event1: SessionExpiredEvent = subscription2Events.poll() as SessionExpiredEvent
        assertEquals(session2.id(), subscription2Event1.session.id())

        subscription1.cancel()

        redisClient.set(
            "uk.co.rafearnold.captainsonar.session.${session1.id()}".toByteArray(Charsets.UTF_8),
            sessionCodec.serialize(session1),
        )
        redisClient.set("uk.co.rafearnold.captainsonar.session-shadow.${session1.id()}", "")
        redisClient.pexpire("uk.co.rafearnold.captainsonar.session-shadow.${session1.id()}", ttlMs)
        Thread.sleep(ttlMs)
        redisClient.keys("*")
        CompletableFuture.runAsync { while (subscription2Events.size != 1); }.get(2, TimeUnit.SECONDS)
        assertEquals(1, subscription2Events.size)
        val subscription2Event2: SessionExpiredEvent = subscription2Events.poll() as SessionExpiredEvent
        assertEquals(session1.id(), subscription2Event2.session.id())
        assertEquals(0, subscription1Events.size)

        subscription2.cancel()

        redisClient.set(
            "uk.co.rafearnold.captainsonar.session.${session2.id()}".toByteArray(Charsets.UTF_8),
            sessionCodec.serialize(session2),
        )
        redisClient.set("uk.co.rafearnold.captainsonar.session-shadow.${session2.id()}", "", SetParams().px(ttlMs))
        Thread.sleep(ttlMs)
        redisClient.keys("*")
        assertEquals(0, subscription1Events.size)
        assertEquals(0, subscription2Events.size)

        val subscription3Events: Queue<SessionEvent> = ConcurrentLinkedQueue()
        eventService.subscribeToSessionEvents { subscription3Events.add(it) }

        redisClient.set(
            "uk.co.rafearnold.captainsonar.session.${session2.id()}".toByteArray(Charsets.UTF_8),
            sessionCodec.serialize(session2),
        )
        redisClient.set("uk.co.rafearnold.captainsonar.session-shadow.${session2.id()}", "", SetParams().px(ttlMs))
        Thread.sleep(ttlMs)
        redisClient.keys("*")
        CompletableFuture.runAsync { while (subscription3Events.size != 1); }.get(2, TimeUnit.SECONDS)
        assertEquals(1, subscription3Events.size)
        val subscription3Event1: SessionExpiredEvent = subscription3Events.poll() as SessionExpiredEvent
        assertEquals(session2.id(), subscription3Event1.session.id())
        assertEquals(0, subscription1Events.size)
        assertEquals(0, subscription2Events.size)

        vertx.close()
    }

    @Test
    @Suppress("ControlFlowWithEmptyBody")
    fun `when the redis client config is changed then the redis subscription is updated`() {
        val vertx: Vertx = Vertx.vertx()

        val appConfig: ObservableMutableMap<String, String> = ObservableMutableMapImpl(ConcurrentHashMap())
        appConfig["redis.connection.host"] = redisContainer1.host
        appConfig["redis.connection.port"] = redisContainer1.firstMappedPort.toString()
        val redisClientProvider = RedisClientProvider(appConfig)
        val sessionCodec: RedisSessionCodec = RedisSessionCodecImpl()
        val eventService =
            RedisSessionEventService(
                redisClientProvider = redisClientProvider,
                sessionCodec = sessionCodec,
            )

        val subscription1Events: Queue<SessionEvent> = ConcurrentLinkedQueue()
        eventService.subscribeToSessionEvents { subscription1Events.add(it) }
        val subscription2Events: Queue<SessionEvent> = ConcurrentLinkedQueue()
        eventService.subscribeToSessionEvents { subscription2Events.add(it) }

        val session1 =
            SharedDataSessionImpl(VertxContextPRNG.current(vertx), 23, SessionStore.DEFAULT_SESSIONID_LENGTH)
        val ttlMs: Long = 100
        val redisClient1 = Jedis(redisContainer1.host, redisContainer1.firstMappedPort)

        redisClient1.set(
            "uk.co.rafearnold.captainsonar.session.${session1.id()}".toByteArray(Charsets.UTF_8),
            sessionCodec.serialize(session1),
        )
        redisClient1.set("uk.co.rafearnold.captainsonar.session-shadow.${session1.id()}", "", SetParams().px(ttlMs))

        // Wait for the entry to expire.
        Thread.sleep(ttlMs)
        // Retrieve all keys from the db to trigger redis to acknowledge the entry that has expired.
        redisClient1.keys("*")

        CompletableFuture.runAsync { while (subscription1Events.size != 1); }.get(2, TimeUnit.SECONDS)
        assertEquals(1, subscription1Events.size)
        val subscription1Event1: SessionExpiredEvent = subscription1Events.poll() as SessionExpiredEvent
        assertEquals(session1.id(), subscription1Event1.session.id())
        CompletableFuture.runAsync { while (subscription2Events.size != 1); }.get(2, TimeUnit.SECONDS)
        assertEquals(1, subscription2Events.size)
        val subscription2Event1: SessionExpiredEvent = subscription2Events.poll() as SessionExpiredEvent
        assertEquals(session1.id(), subscription2Event1.session.id())

        appConfig["redis.connection.host"] = redisContainer2.host
        appConfig["redis.connection.port"] = redisContainer2.firstMappedPort.toString()
        appConfig["redis.connection.password"] = redisContainer2Password

        val redisClient2 =
            Jedis(
                redisContainer2.host,
                redisContainer2.firstMappedPort,
                DefaultJedisClientConfig.builder().password(redisContainer2Password).build()
            )

        val session2 =
            SharedDataSessionImpl(VertxContextPRNG.current(vertx), 45, SessionStore.DEFAULT_SESSIONID_LENGTH)
        redisClient2.set(
            "uk.co.rafearnold.captainsonar.session.${session2.id()}".toByteArray(Charsets.UTF_8),
            sessionCodec.serialize(session2),
        )
        redisClient2.set("uk.co.rafearnold.captainsonar.session-shadow.${session2.id()}", "", SetParams().px(ttlMs))

        // Wait for the entry to expire.
        Thread.sleep(ttlMs)
        // Retrieve all keys from the db to trigger redis to acknowledge the entry that has expired.
        redisClient2.keys("*")
        CompletableFuture.runAsync { while (subscription1Events.size != 1); }.get(2, TimeUnit.SECONDS)
        assertEquals(1, subscription1Events.size)
        val subscription1Event2: SessionExpiredEvent = subscription1Events.poll() as SessionExpiredEvent
        assertEquals(session2.id(), subscription1Event2.session.id())
        CompletableFuture.runAsync { while (subscription2Events.size != 1); }.get(2, TimeUnit.SECONDS)
        assertEquals(1, subscription2Events.size)
        val subscription2Event2: SessionExpiredEvent = subscription2Events.poll() as SessionExpiredEvent
        assertEquals(session2.id(), subscription2Event2.session.id())

        vertx.close()
    }
}
