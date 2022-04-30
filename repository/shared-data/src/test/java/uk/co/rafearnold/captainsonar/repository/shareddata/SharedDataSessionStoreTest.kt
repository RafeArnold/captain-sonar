package uk.co.rafearnold.captainsonar.repository.shareddata

import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.unmockkAll
import io.vertx.core.Vertx
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
import org.junit.jupiter.api.fail
import uk.co.rafearnold.captainsonar.common.toCompletableFuture
import uk.co.rafearnold.captainsonar.repository.session.SessionCodec
import uk.co.rafearnold.captainsonar.repository.session.SessionCodecImpl
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import uk.co.rafearnold.captainsonar.shareddata.SharedMap
import uk.co.rafearnold.captainsonar.shareddata.simple.SimpleClusterManager
import uk.co.rafearnold.captainsonar.shareddata.getDistributedMap
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutputStream
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
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = mockk(), sessionCodec = mockk())
        assertEquals(5000, sessionStore.retryTimeout())
        vertx.close()
    }

    @Test
    fun `a session can be created`() {
        val vertx: Vertx = Vertx.vertx()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = mockk(), sessionCodec = mockk())
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
        val sessionMap: SharedMap<String, ByteArray> = sharedDataService.getDistributedMap("test_dataMapName")
        val sessionCodec: SessionCodec = SessionCodecImpl()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap, sessionCodec = sessionCodec)

        val timeout: Long = 10000
        val length = 17
        val session: SharedDataSessionImpl = sessionStore.createSession(timeout, length) as SharedDataSessionImpl

        sessionStore.put(session).toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals(setOf(session.id()), sessionMap.keys)

        val result1: ByteArray? = sessionMap[session.id()]
        val result1Stream = DataInputStream(ByteArrayInputStream(result1))
        assertEquals(session.id().length, result1Stream.readInt())
        assertEquals(session.id(), result1Stream.readUtf(session.id().length))
        assertEquals(timeout, result1Stream.readLong())
        assertEquals(session.lastAccessed(), result1Stream.readLong())
        assertEquals(session.version(), result1Stream.readInt())
        // No data.
        assertEquals(0, result1Stream.readInt())
        // End of file.
        assertEquals(-1, result1Stream.read())

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

        val result2: ByteArray? = sessionMap[session.id()]
        val result2Stream = DataInputStream(ByteArrayInputStream(result2))
        assertEquals(session.id().length, result2Stream.readInt())
        assertEquals(session.id(), result2Stream.readUtf(session.id().length))
        assertEquals(timeout, result2Stream.readLong())
        assertEquals(session.lastAccessed(), result2Stream.readLong())
        assertEquals(session.version(), result2Stream.readInt())
        assertEquals(session.data().size, result2Stream.readInt())
        for ((key: String, value: Any) in session.data()) {
            assertEquals(key.length, result2Stream.readInt())
            assertEquals(key, result2Stream.readUtf(key.length))
            when (value) {
                dataValue1 -> {
                    assertEquals(9, result2Stream.readByte())
                    assertEquals(dataValue1.length, result2Stream.readInt())
                    assertEquals(dataValue1, result2Stream.readUtf(dataValue1.length))
                }
                dataValue2 -> {
                    assertEquals(2, result2Stream.readByte())
                    assertEquals(2353, result2Stream.readInt())
                }
                dataValue3 -> {
                    assertEquals(8, result2Stream.readByte())
                    assertEquals(0, result2Stream.readByte())
                }
                else -> fail("Unknown key: $key")
            }
        }
        // End of file.
        assertEquals(-1, result2Stream.read())

        vertx.close()
    }

    @Test
    fun `when a session is inserted with the same id as an existing session but a different version then an exception is thrown`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, ByteArray> = sharedDataService.getDistributedMap("test_dataMapName")
        val sessionCodec: SessionCodec = SessionCodecImpl()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap, sessionCodec = sessionCodec)

        val timeout: Long = 10000
        val length = 17
        val session: SharedDataSessionImpl = sessionStore.createSession(timeout, length) as SharedDataSessionImpl

        sessionStore.put(session).toCompletableFuture().get(2, TimeUnit.SECONDS)

        // Update version and try to reinsert. We also have to add some data before updating the
        // version to ensure the checksum is updated.
        session.put("test_key", "test_value")
        session.incrementVersion()
        val executionException: ExecutionException =
            assertThrows {
                sessionStore.put(session).toCompletableFuture().get(2, TimeUnit.SECONDS)
            }
        assertEquals("Version mismatch", executionException.cause?.message)

        vertx.close()
    }

    @Test
    fun `when a session is inserted then its version is incremented`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, ByteArray> = sharedDataService.getDistributedMap("test_dataMapName")
        val sessionCodec: SessionCodec = SessionCodecImpl()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap, sessionCodec = sessionCodec)

        val timeout: Long = 10000
        val length = 17
        val session: SharedDataSessionImpl = sessionStore.createSession(timeout, length) as SharedDataSessionImpl
        val originalVersion: Int = session.version()

        sessionStore.put(session).toCompletableFuture().get(2, TimeUnit.SECONDS)

        // Add some data to the session and reinsert. The session's data has to be updated to
        // ensure the checksum is updated.
        session.put("test_key", "test_value")
        sessionStore.put(session).toCompletableFuture().get(2, TimeUnit.SECONDS)

        val result: ByteArray? = sessionMap[session.id()]
        val resultStream = DataInputStream(ByteArrayInputStream(result))
        assertEquals(session.id().length, resultStream.readInt())
        assertEquals(session.id(), resultStream.readUtf(session.id().length))
        assertEquals(timeout, resultStream.readLong())
        assertEquals(session.lastAccessed(), resultStream.readLong())
        // Verify the version has incremented.
        assertEquals(originalVersion + 1, resultStream.readInt())

        vertx.close()
    }

    @Test
    fun `sessions are inserted with a expiration time`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, ByteArray> = sharedDataService.getDistributedMap("test_dataMapName")
        val sessionCodec: SessionCodec = SessionCodecImpl()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap, sessionCodec = sessionCodec)

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
        val sessionMap: SharedMap<String, ByteArray> = sharedDataService.getDistributedMap("test_dataMapName")
        val sessionCodec: SessionCodec = SessionCodecImpl()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap, sessionCodec = sessionCodec)

        // Session with no data.
        run {
            val length = 17
            val id: String = Base64.getEncoder().encodeToString(Random.nextBytes(length))
            val timeout: Long = 10000
            val lastAccessed: Long = System.currentTimeMillis()
            val version = 3456

            val serializedSessionOutputStream = ByteArrayOutputStream()
            val dataOutputStream = DataOutputStream(serializedSessionOutputStream)
            dataOutputStream.writeInt(id.length)
            dataOutputStream.write(id.toByteArray(Charsets.UTF_8))
            dataOutputStream.writeLong(timeout)
            dataOutputStream.writeLong(lastAccessed)
            dataOutputStream.writeInt(version)
            dataOutputStream.writeInt(0)

            sessionMap[id] = serializedSessionOutputStream.toByteArray()

            val result: Session? = sessionStore.get(id).toCompletableFuture().get(2, TimeUnit.SECONDS)
            assertTrue(result is SharedDataSessionImpl)
            result as SharedDataSessionImpl
            assertEquals(id, result.id())
            assertEquals(timeout, result.timeout())
            assertEquals(lastAccessed, result.lastAccessed())
            assertEquals(version, result.version())
            assertTrue(result.isEmpty)
            assertEquals(mapOf<String, Any>(), result.data())
        }

        // Session with data.
        run {
            val length = 345
            val id: String = Base64.getEncoder().encodeToString(Random.nextBytes(length))
            val timeout: Long = 543665
            val lastAccessed: Long = System.currentTimeMillis()
            val version = 17
            val dataKey1 = "test_dataKey1"
            val dataValue1 = 2353
            val dataKey2 = "test_dataKey2"
            val dataValue2 = false
            val dataKey3 = "test_dataKey3"
            val dataValue3 = "test_dataValue1"

            val serializedSessionOutputStream = ByteArrayOutputStream()
            val dataOutputStream = DataOutputStream(serializedSessionOutputStream)
            dataOutputStream.writeInt(id.length)
            dataOutputStream.write(id.toByteArray(Charsets.UTF_8))
            dataOutputStream.writeLong(timeout)
            dataOutputStream.writeLong(lastAccessed)
            dataOutputStream.writeInt(version)
            dataOutputStream.writeInt(3)
            dataOutputStream.writeInt(dataKey1.length)
            dataOutputStream.write(dataKey1.toByteArray(Charsets.UTF_8))
            dataOutputStream.write(2)
            dataOutputStream.writeInt(dataValue1)
            dataOutputStream.writeInt(dataKey2.length)
            dataOutputStream.write(dataKey2.toByteArray(Charsets.UTF_8))
            dataOutputStream.write(8)
            dataOutputStream.writeBoolean(dataValue2)
            dataOutputStream.writeInt(dataKey3.length)
            dataOutputStream.write(dataKey3.toByteArray(Charsets.UTF_8))
            dataOutputStream.write(9)
            dataOutputStream.writeInt(dataValue3.length)
            dataOutputStream.write(dataValue3.toByteArray(Charsets.UTF_8))

            sessionMap[id] = serializedSessionOutputStream.toByteArray()

            val result: Session? = sessionStore.get(id).toCompletableFuture().get(2, TimeUnit.SECONDS)
            assertTrue(result is SharedDataSessionImpl)
            result as SharedDataSessionImpl
            assertEquals(id, result.id())
            assertEquals(timeout, result.timeout())
            assertEquals(lastAccessed, result.lastAccessed())
            assertEquals(version, result.version())
            assertFalse(result.isEmpty)
            assertEquals(mapOf(dataKey1 to dataValue1, dataKey2 to dataValue2, dataKey3 to dataValue3), result.data())
        }

        vertx.close()
    }

    @Test
    fun `when a session's shadow key has expired then null is returned when the session is requested`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, ByteArray> = sharedDataService.getDistributedMap("test_dataMapName")
        val sessionCodec: SessionCodec = SessionCodecImpl()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap, sessionCodec = sessionCodec)

        // Session with no data.
        run {
            val length = 17
            val id: String = Base64.getEncoder().encodeToString(Random.nextBytes(length))
            val timeout: Long = 10000
            val lastAccessed: Long = System.currentTimeMillis()
            val version = 3456

            val serializedSessionOutputStream = ByteArrayOutputStream()
            val dataOutputStream = DataOutputStream(serializedSessionOutputStream)
            dataOutputStream.writeInt(id.length)
            dataOutputStream.write(id.toByteArray(Charsets.UTF_8))
            dataOutputStream.writeLong(timeout)
            dataOutputStream.writeLong(lastAccessed)
            dataOutputStream.writeInt(version)
            dataOutputStream.writeInt(0)

            sessionMap["uk.co.rafearnold.captainsonar.session.$id"] = serializedSessionOutputStream.toByteArray()

            assertNull(sessionStore.get(id).toCompletableFuture().get(2, TimeUnit.SECONDS))
        }

        // Session with data.
        run {
            val length = 345
            val id: String = Base64.getEncoder().encodeToString(Random.nextBytes(length))
            val timeout: Long = 543665
            val lastAccessed: Long = System.currentTimeMillis()
            val version = 17
            val dataKey1 = "test_dataKey1"
            val dataValue1 = 2353
            val dataKey2 = "test_dataKey2"
            val dataValue2 = false
            val dataKey3 = "test_dataKey3"
            val dataValue3 = "test_dataValue1"

            val serializedSessionOutputStream = ByteArrayOutputStream()
            val dataOutputStream = DataOutputStream(serializedSessionOutputStream)
            dataOutputStream.writeInt(id.length)
            dataOutputStream.write(id.toByteArray(Charsets.UTF_8))
            dataOutputStream.writeLong(timeout)
            dataOutputStream.writeLong(lastAccessed)
            dataOutputStream.writeInt(version)
            dataOutputStream.writeInt(3)
            dataOutputStream.writeInt(dataKey1.length)
            dataOutputStream.write(dataKey1.toByteArray(Charsets.UTF_8))
            dataOutputStream.write(2)
            dataOutputStream.writeInt(dataValue1)
            dataOutputStream.writeInt(dataKey2.length)
            dataOutputStream.write(dataKey2.toByteArray(Charsets.UTF_8))
            dataOutputStream.write(8)
            dataOutputStream.writeBoolean(dataValue2)
            dataOutputStream.writeInt(dataKey3.length)
            dataOutputStream.write(dataKey3.toByteArray(Charsets.UTF_8))
            dataOutputStream.write(9)
            dataOutputStream.writeInt(dataValue3.length)
            dataOutputStream.write(dataValue3.toByteArray(Charsets.UTF_8))

            sessionMap["uk.co.rafearnold.captainsonar.session.$id"] = serializedSessionOutputStream.toByteArray()

            assertNull(sessionStore.get(id).toCompletableFuture().get(2, TimeUnit.SECONDS))
        }

        vertx.close()
    }

    @Test
    fun `the id of a session can be regenerated after being retrieved`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, ByteArray> = sharedDataService.getDistributedMap("test_dataMapName")
        val sessionCodec: SessionCodec = SessionCodecImpl()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap, sessionCodec = sessionCodec)

        val length = 17
        val id: String = Base64.getEncoder().encodeToString(Random.nextBytes(length))
        val timeout: Long = 10000
        val lastAccessed: Long = System.currentTimeMillis()
        val version = 675

        val serializedSessionOutputStream = ByteArrayOutputStream()
        val dataOutputStream = DataOutputStream(serializedSessionOutputStream)
        dataOutputStream.writeInt(id.length)
        dataOutputStream.write(id.toByteArray(Charsets.UTF_8))
        dataOutputStream.writeLong(timeout)
        dataOutputStream.writeLong(lastAccessed)
        dataOutputStream.writeInt(version)
        dataOutputStream.writeInt(0)

        sessionMap["uk.co.rafearnold.captainsonar.session.$id"] = serializedSessionOutputStream.toByteArray()

        val result: Session? = sessionStore.get(id).toCompletableFuture().get(2, TimeUnit.SECONDS)

        // Regenerate ID and verify it's different to the original.
        result?.regenerateId()
        assertNotEquals(id, result?.id())

        vertx.close()
    }

    @Test
    fun `a session can be deleted by id`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, ByteArray> = sharedDataService.getDistributedMap("test_dataMapName")
        val sessionCodec: SessionCodec = SessionCodecImpl()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap, sessionCodec = sessionCodec)

        val id1: String = Base64.getEncoder().encodeToString(Random.nextBytes(17))
        run {
            val timeout: Long = 10000
            val lastAccessed: Long = System.currentTimeMillis()
            val version = 675

            val serializedSessionOutputStream = ByteArrayOutputStream()
            val dataOutputStream = DataOutputStream(serializedSessionOutputStream)
            dataOutputStream.writeInt(id1.length)
            dataOutputStream.write(id1.toByteArray(Charsets.UTF_8))
            dataOutputStream.writeLong(timeout)
            dataOutputStream.writeLong(lastAccessed)
            dataOutputStream.writeInt(version)
            dataOutputStream.writeInt(0)

            sessionMap[id1] = serializedSessionOutputStream.toByteArray()
        }

        val id2: String = Base64.getEncoder().encodeToString(Random.nextBytes(3464))
        run {
            val timeout: Long = 543665
            val lastAccessed: Long = System.currentTimeMillis()
            val version = 17
            val dataKey1 = "test_dataKey1"
            val dataValue1 = 2353
            val dataKey2 = "test_dataKey2"
            val dataValue2 = false
            val dataKey3 = "test_dataKey3"
            val dataValue3 = "test_dataValue1"

            val serializedSessionOutputStream = ByteArrayOutputStream()
            val dataOutputStream = DataOutputStream(serializedSessionOutputStream)
            dataOutputStream.writeInt(id2.length)
            dataOutputStream.write(id2.toByteArray(Charsets.UTF_8))
            dataOutputStream.writeLong(timeout)
            dataOutputStream.writeLong(lastAccessed)
            dataOutputStream.writeInt(version)
            dataOutputStream.writeInt(3)
            dataOutputStream.writeInt(dataKey1.length)
            dataOutputStream.write(dataKey1.toByteArray(Charsets.UTF_8))
            dataOutputStream.write(2)
            dataOutputStream.writeInt(dataValue1)
            dataOutputStream.writeInt(dataKey2.length)
            dataOutputStream.write(dataKey2.toByteArray(Charsets.UTF_8))
            dataOutputStream.write(8)
            dataOutputStream.writeBoolean(dataValue2)
            dataOutputStream.writeInt(dataKey3.length)
            dataOutputStream.write(dataKey3.toByteArray(Charsets.UTF_8))
            dataOutputStream.write(9)
            dataOutputStream.writeInt(dataValue3.length)
            dataOutputStream.write(dataValue3.toByteArray(Charsets.UTF_8))

            sessionMap[id2] = serializedSessionOutputStream.toByteArray()
        }

        assertEquals(setOf(id1, id2), sessionMap.keys)

        sessionStore.delete(id1).toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals(setOf(id2), sessionMap.keys)

        sessionStore.delete(id2).toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals(setOf<String>(), sessionMap.keys)

        vertx.close()
    }

    @Test
    fun `all sessions can be deleted`() {
        val vertx: Vertx = Vertx.vertx()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val sessionMap: SharedMap<String, ByteArray> = sharedDataService.getDistributedMap("test_dataMapName")
        val sessionCodec: SessionCodec = SessionCodecImpl()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap, sessionCodec = sessionCodec)

        val session1Id = Base64.getEncoder().encodeToString(Random.nextBytes(17))
        val session2Id = Base64.getEncoder().encodeToString(Random.nextBytes(3464))

        sessionMap[session1Id] = byteArrayOf()
        sessionMap[session2Id] = byteArrayOf()

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
        val sessionMap: SharedMap<String, ByteArray> = sharedDataService.getDistributedMap("test_dataMapName")
        val sessionCodec: SessionCodec = SessionCodecImpl()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap, sessionCodec = sessionCodec)

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
        val sessionMap: SharedMap<String, ByteArray> = sharedDataService.getDistributedMap("test_dataMapName")
        val sessionCodec: SessionCodec = SessionCodecImpl()
        val sessionStore = SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap, sessionCodec = sessionCodec)

        val session1Id = Base64.getEncoder().encodeToString(Random.nextBytes(17))
        val session2Id = Base64.getEncoder().encodeToString(Random.nextBytes(3464))
        val session3Id = Base64.getEncoder().encodeToString(Random.nextBytes(45))

        sessionMap[session1Id] = byteArrayOf()
        sessionMap[session2Id] = byteArrayOf()
        sessionMap[session3Id] = byteArrayOf()

        assertEquals(3, sessionStore.size().toCompletableFuture().get(2, TimeUnit.SECONDS))

        vertx.close()
    }

    private fun DataInput.readUtf(length: Int): String =
        ByteArray(length).apply { this@readUtf.readFully(this) }.toString(Charsets.UTF_8)
}
