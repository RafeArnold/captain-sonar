package uk.co.rafearnold.captainsonar.repository.shareddata

import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import io.vertx.core.Vertx
import io.vertx.ext.auth.VertxContextPRNG
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.co.rafearnold.captainsonar.common.Subscription
import uk.co.rafearnold.captainsonar.repository.session.SessionEvent
import uk.co.rafearnold.captainsonar.repository.session.SessionExpiredEvent
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import uk.co.rafearnold.captainsonar.shareddata.SharedMap
import uk.co.rafearnold.captainsonar.shareddata.simple.SimpleClusterManager
import uk.co.rafearnold.captainsonar.shareddata.getDistributedMap
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class SharedDataSessionEventServiceTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
        SimpleClusterManager.clearAllClusters()
    }

    @Test
    @Suppress("ControlFlowWithEmptyBody")
    fun `session events can be subscribed to and unsubscribed from`() {
        val vertx: Vertx = Vertx.vertx()

        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val dataMap: SharedMap<String, SharedDataSessionImpl> =
            sharedDataService.getDistributedMap(name = "test_dataMapName")
        val eventService = SharedDataSessionEventService(dataMap = dataMap)

        val timeout: Long = 10

        val subscription1Events: Queue<SessionEvent> = ConcurrentLinkedQueue()
        val subscription1: Subscription = eventService.subscribeToSessionEvents { subscription1Events.add(it) }

        val session1 = SharedDataSessionImpl(VertxContextPRNG.current(vertx), timeout, 25)
        session1.data()["test_dataKey1"] = "test_dataValue1"
        dataMap.put(session1.id(), session1, timeout, TimeUnit.MILLISECONDS)

        Thread.sleep(timeout)

        CompletableFuture.runAsync { while (subscription1Events.size == 0); }.get(2, TimeUnit.SECONDS)
        val subscription1Event1: SessionExpiredEvent = subscription1Events.poll() as SessionExpiredEvent
        assertEquals(session1.id(), subscription1Event1.session.id())
        assertEquals(timeout, subscription1Event1.session.timeout())
        assertEquals(session1.lastAccessed(), subscription1Event1.session.lastAccessed())
        assertEquals(session1.version(), subscription1Event1.session.version())
        assertEquals(session1.data(), subscription1Event1.session.data())

        val subscription2Events: Queue<SessionEvent> = ConcurrentLinkedQueue()
        val subscription2: Subscription = eventService.subscribeToSessionEvents { subscription2Events.add(it) }

        val session2 = SharedDataSessionImpl(VertxContextPRNG.current(vertx), timeout, 5)
        session2.data()["test_dataKey2"] = "test_dataValue2"
        session2.data()["test_dataKey3"] = "test_dataValue3"
        session2.data()["test_dataKey4"] = "test_dataValue4"
        dataMap.put(session2.id(), session2, timeout, TimeUnit.MILLISECONDS)

        Thread.sleep(timeout)

        CompletableFuture.runAsync { while (subscription1Events.size != 1); }.get(2, TimeUnit.SECONDS)
        val subscription1Event2: SessionExpiredEvent = subscription1Events.poll() as SessionExpiredEvent
        assertEquals(session2.id(), subscription1Event2.session.id())
        assertEquals(timeout, subscription1Event2.session.timeout())
        assertEquals(session2.lastAccessed(), subscription1Event2.session.lastAccessed())
        assertEquals(session2.version(), subscription1Event2.session.version())
        assertEquals(session2.data(), subscription1Event2.session.data())

        CompletableFuture.runAsync { while (subscription2Events.size != 1); }.get(2, TimeUnit.SECONDS)
        val subscription2Event1: SessionExpiredEvent = subscription2Events.poll() as SessionExpiredEvent
        assertEquals(session2.id(), subscription2Event1.session.id())
        assertEquals(timeout, subscription2Event1.session.timeout())
        assertEquals(session2.lastAccessed(), subscription2Event1.session.lastAccessed())
        assertEquals(session2.version(), subscription2Event1.session.version())
        assertEquals(session2.data(), subscription2Event1.session.data())

        subscription1.cancel()

        val session3 = SharedDataSessionImpl(VertxContextPRNG.current(vertx), timeout, 73)
        session3.data()["test_dataKey5"] = "test_dataValue5"
        session3.data()["test_dataKey6"] = "test_dataValue6"
        dataMap.put(session3.id(), session3, timeout, TimeUnit.MILLISECONDS)

        Thread.sleep(timeout)

        CompletableFuture.runAsync { while (subscription2Events.size != 1); }.get(2, TimeUnit.SECONDS)
        val subscription2Event2: SessionExpiredEvent = subscription2Events.poll() as SessionExpiredEvent
        assertEquals(session3.id(), subscription2Event2.session.id())
        assertEquals(timeout, subscription2Event2.session.timeout())
        assertEquals(session3.lastAccessed(), subscription2Event2.session.lastAccessed())
        assertEquals(session3.version(), subscription2Event2.session.version())
        assertEquals(session3.data(), subscription2Event2.session.data())
        assertEquals(0, subscription1Events.size)

        subscription2.cancel()

        val session4 = SharedDataSessionImpl(VertxContextPRNG.current(vertx), timeout, 53)
        dataMap.put(session4.id(), session4, timeout, TimeUnit.MILLISECONDS)

        Thread.sleep(timeout)

        assertEquals(0, subscription1Events.size)
        assertEquals(0, subscription2Events.size)

        vertx.close()
    }
}
