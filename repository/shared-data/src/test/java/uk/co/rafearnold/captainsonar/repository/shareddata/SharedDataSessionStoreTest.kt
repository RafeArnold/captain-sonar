package uk.co.rafearnold.captainsonar.repository.shareddata

import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.unmockkAll
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.ext.auth.VertxContextPRNG
import io.vertx.ext.web.Session
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.co.rafearnold.captainsonar.common.toCompletableFuture
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import uk.co.rafearnold.captainsonar.shareddata.SharedMap
import uk.co.rafearnold.captainsonar.shareddata.getDistributedMap
import uk.co.rafearnold.captainsonar.shareddata.simple.SimpleClusterManager
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class SharedDataSessionStoreTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
        SimpleClusterManager.clearAllClusters()
    }

    @Test
    fun `retry timeout is 5 seconds`() {
        val vertx: Vertx = Vertx.vertx()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = mockk())
        assertEquals(5000, sessionStore.retryTimeout())
        vertx.close()
    }

    @Test
    fun `a session can be created`() {
        val vertx: Vertx = Vertx.vertx()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = mockk())
        val timeout: Long = 10000
        val length = 17
        val session: Session = sessionStore.createSession(timeout, length)
        assertTrue(session is SharedDataSessionImpl)
        assertEquals(2 * length, session.id().length)
        assertEquals(timeout, session.timeout())
        vertx.close()
    }

    @Test
    fun `a session can be inserted into the db`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, SharedDataSessionImpl> =
            sharedDataService.getDistributedMap("test_dataMapName")
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap)

        val timeout: Long = 10000
        val length = 17
        val session: SharedDataSessionImpl = sessionStore.createSession(timeout, length) as SharedDataSessionImpl

        sessionStore.put(session).toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals(setOf(session.id()), sessionMap.keys)

        val result1: SharedDataSessionImpl? = sessionMap[session.id()]
        assertEquals(session.id(), result1?.id())
        assertEquals(timeout, result1?.timeout())
        assertEquals(session.lastAccessed(), result1?.lastAccessed())
        assertEquals(session.version(), result1?.version())
        // No data.
        assertEquals(0, result1?.data()?.size)

        // Insert some data into the session and update other values, then reinsert into the db.
        val dataKey1 = "test_dataKey1"
        val dataValue1 = "test_dataValue1"
        val dataKey2 = "test_dataKey2"
        val dataValue2 = 2353
        val dataKey3 = "test_dataKey3"
        val dataValue3 = false
        session.put(dataKey1, dataValue1)
        session.put(dataKey2, dataValue2)
        session.put(dataKey3, dataValue3)
        sessionStore.put(session).toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals(setOf(session.id()), sessionMap.keys)

        val result2: SharedDataSessionImpl? = sessionMap[session.id()]
        assertEquals(session.id(), result2?.id())
        assertEquals(timeout, result2?.timeout())
        assertEquals(session.lastAccessed(), result2?.lastAccessed())
        assertEquals(session.version(), result2?.version())
        assertEquals(session.data(), result2?.data())

        vertx.close()
    }

    @Test
    fun `when a session is inserted with the same id as an existing session but a different version then an exception is thrown`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, SharedDataSessionImpl> =
            sharedDataService.getDistributedMap(name = "test_dataMapName")
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap)

        val timeout: Long = 10000
        val length = 17
        val session: SharedDataSessionImpl = sessionStore.createSession(timeout, length) as SharedDataSessionImpl
        val sessionCopy: SharedDataSessionImpl =
            SharedDataSessionImpl().apply { readFromBuffer(0, Buffer.buffer().apply { session.writeToBuffer(this) }) }

        sessionStore.put(session).toCompletableFuture().get(2, TimeUnit.SECONDS)

        // Update version and try to reinsert. We also have to add some data before updating the
        // version to ensure the checksum is updated.
        session.put("test_key", "test_value")
        session.incrementVersion()
        val executionException: ExecutionException =
            assertThrows {
                sessionStore.put(sessionCopy).toCompletableFuture().get(2, TimeUnit.SECONDS)
            }
        assertEquals("Version mismatch", executionException.cause?.message)

        vertx.close()
    }

    @Test
    fun `when a session is inserted then its version is incremented`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, SharedDataSessionImpl> =
            sharedDataService.getDistributedMap(name = "test_dataMapName")
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap)

        val timeout: Long = 10000
        val length = 17
        val session: SharedDataSessionImpl = sessionStore.createSession(timeout, length) as SharedDataSessionImpl
        val originalVersion: Int = session.version()

        sessionStore.put(session).toCompletableFuture().get(2, TimeUnit.SECONDS)

        // Add some data to the session and reinsert. The session's data has to be updated to
        // ensure the checksum is updated.
        session.put("test_key", "test_value")
        sessionStore.put(session).toCompletableFuture().get(2, TimeUnit.SECONDS)

        val result: SharedDataSessionImpl? = sessionMap[session.id()]
        assertEquals(session.id(), result?.id())
        assertEquals(timeout, result?.timeout())
        assertEquals(session.lastAccessed(), result?.lastAccessed())
        // Verify the version has incremented.
        assertEquals(originalVersion + 1, result?.version())

        vertx.close()
    }

    @Test
    fun `sessions are inserted with a expiration time`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, SharedDataSessionImpl> =
            sharedDataService.getDistributedMap(name = "test_dataMapName")
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap)

        val timeout: Long = 50
        val length = 17
        val session: SharedDataSessionImpl = sessionStore.createSession(timeout, length) as SharedDataSessionImpl

        sessionStore.put(session).toCompletableFuture().get(2, TimeUnit.SECONDS)

        Thread.sleep(timeout / 2)

        assertEquals(setOf(session.id()), sessionMap.keys)

        Thread.sleep(timeout)

        assertEquals(setOf<String>(), sessionMap.keys)

        vertx.close()
    }

    @Test
    fun `a session can be retrieved by its id`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, SharedDataSessionImpl> =
            sharedDataService.getDistributedMap(name = "test_dataMapName")
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap)

        // Session with no data.
        run {
            val length = 17
            val timeout: Long = 10000
            val session = SharedDataSessionImpl(VertxContextPRNG.current(vertx), timeout, length)

            sessionMap[session.id()] = session

            val result: Session? = sessionStore.get(session.id()).toCompletableFuture().get(2, TimeUnit.SECONDS)
            assertTrue(result is SharedDataSessionImpl)
            result as SharedDataSessionImpl
            assertEquals(session.id(), result.id())
            assertEquals(timeout, result.timeout())
            assertEquals(session.lastAccessed(), result.lastAccessed())
            assertEquals(session.version(), result.version())
            assertTrue(result.isEmpty)
            assertEquals(mapOf<String, Any>(), result.data())
        }

        // Session with data.
        run {
            val length = 345
            val timeout: Long = 543665
            val dataKey1 = "test_dataKey1"
            val dataValue1 = 2353
            val dataKey2 = "test_dataKey2"
            val dataValue2 = false
            val dataKey3 = "test_dataKey3"
            val dataValue3 = "test_dataValue1"
            val session = SharedDataSessionImpl(VertxContextPRNG.current(vertx), timeout, length)
            session.data()[dataKey1] = dataValue1
            session.data()[dataKey2] = dataValue2
            session.data()[dataKey3] = dataValue3

            sessionMap[session.id()] = session

            val result: Session? = sessionStore.get(session.id()).toCompletableFuture().get(2, TimeUnit.SECONDS)
            assertTrue(result is SharedDataSessionImpl)
            result as SharedDataSessionImpl
            assertEquals(session.id(), result.id())
            assertEquals(timeout, result.timeout())
            assertEquals(session.lastAccessed(), result.lastAccessed())
            assertEquals(session.version(), result.version())
            assertFalse(result.isEmpty)
            assertEquals(mapOf(dataKey1 to dataValue1, dataKey2 to dataValue2, dataKey3 to dataValue3), result.data())
        }

        vertx.close()
    }

    @Test
    fun `when a session has expired then null is returned when the session is requested`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, SharedDataSessionImpl> =
            sharedDataService.getDistributedMap(name = "test_dataMapName")
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap)

        // Session with no data.
        run {
            val length = 17
            val timeout: Long = 10000
            val session = SharedDataSessionImpl(VertxContextPRNG.current(vertx), timeout, length)

            val ttlMs: Long = 10
            sessionMap.put(key = session.id(), value = session, ttl = ttlMs, ttlUnit = TimeUnit.MILLISECONDS)

            Thread.sleep(2 * ttlMs)

            assertNull(sessionStore.get(session.id()).toCompletableFuture().get(2, TimeUnit.SECONDS))
        }

        // Session with data.
        run {
            val length = 345
            val timeout: Long = 543665
            val dataKey1 = "test_dataKey1"
            val dataValue1 = 2353
            val dataKey2 = "test_dataKey2"
            val dataValue2 = false
            val dataKey3 = "test_dataKey3"
            val dataValue3 = "test_dataValue1"
            val session = SharedDataSessionImpl(VertxContextPRNG.current(vertx), timeout, length)
            session.data()[dataKey1] = dataValue1
            session.data()[dataKey2] = dataValue2
            session.data()[dataKey3] = dataValue3

            val ttlMs: Long = 10
            sessionMap.put(key = session.id(), value = session, ttl = ttlMs, ttlUnit = TimeUnit.MILLISECONDS)

            Thread.sleep(2 * ttlMs)

            assertNull(sessionStore.get(session.id()).toCompletableFuture().get(2, TimeUnit.SECONDS))
        }

        vertx.close()
    }

    @Test
    fun `the id of a session can be regenerated after being retrieved`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, SharedDataSessionImpl> =
            sharedDataService.getDistributedMap(name = "test_dataMapName")
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap)

        val length = 17
        val timeout: Long = 10000
        val session = SharedDataSessionImpl(VertxContextPRNG.current(vertx), timeout, length)
        val originalId: String = session.id()

        sessionMap[session.id()] = session

        val result: Session? = sessionStore.get(session.id()).toCompletableFuture().get(2, TimeUnit.SECONDS)

        // Regenerate ID and verify it's different to the original.
        result?.regenerateId()
        assertNotEquals(originalId, result?.id())

        vertx.close()
    }

    @Test
    fun `a session can be deleted by id`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, SharedDataSessionImpl> =
            sharedDataService.getDistributedMap(name = "test_dataMapName")
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap)

        val session1 = SharedDataSessionImpl(VertxContextPRNG.current(vertx), 10000, 17)

        sessionMap[session1.id()] = session1

        val session2 = SharedDataSessionImpl(VertxContextPRNG.current(vertx), 543665, 32)
        session2.data()["test_dataKey1"] = 2353
        session2.data()["test_dataKey2"] = false
        session2.data()["test_dataKey3"] = "test_dataValue3"

        sessionMap[session2.id()] = session2

        assertEquals(setOf(session1.id(), session2.id()), sessionMap.keys)

        sessionStore.delete(session1.id()).toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals(setOf(session2.id()), sessionMap.keys)

        sessionStore.delete(session2.id()).toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals(setOf<String>(), sessionMap.keys)

        vertx.close()
    }

    @Test
    fun `all sessions can be deleted`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, SharedDataSessionImpl> =
            sharedDataService.getDistributedMap(name = "test_dataMapName")
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap)

        val session1Id = Base64.getEncoder().encodeToString(Random.nextBytes(17))
        val session2Id = Base64.getEncoder().encodeToString(Random.nextBytes(3464))

        sessionMap[session1Id] = SharedDataSessionImpl(VertxContextPRNG.current(vertx))
        sessionMap[session2Id] = SharedDataSessionImpl(VertxContextPRNG.current(vertx))

        assertEquals(setOf(session1Id, session2Id), sessionMap.keys)

        sessionStore.clear().toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals(setOf<String>(), sessionMap.keys)

        vertx.close()
    }

    @Test
    fun `when all sessions are cleared but there are no sessions in the db then no operation is performed`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, SharedDataSessionImpl> =
            sharedDataService.getDistributedMap(name = "test_dataMapName")
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap)

        assertEquals(setOf<String>(), sessionMap.keys)

        sessionStore.clear().toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals(setOf<String>(), sessionMap.keys)

        vertx.close()
    }

    @Test
    fun `the number of sessions in the db can be retrieved`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, SharedDataSessionImpl> =
            sharedDataService.getDistributedMap(name = "test_dataMapName")
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap)

        val session1Id = Base64.getEncoder().encodeToString(Random.nextBytes(17))
        val session2Id = Base64.getEncoder().encodeToString(Random.nextBytes(3464))
        val session3Id = Base64.getEncoder().encodeToString(Random.nextBytes(45))

        sessionMap[session1Id] = SharedDataSessionImpl(VertxContextPRNG.current(vertx))
        sessionMap[session2Id] = SharedDataSessionImpl(VertxContextPRNG.current(vertx))
        sessionMap[session3Id] = SharedDataSessionImpl(VertxContextPRNG.current(vertx))

        assertEquals(3, sessionStore.size().toCompletableFuture().get(2, TimeUnit.SECONDS))

        vertx.close()
    }
}
