package uk.co.rafearnold.captainsonar.repository.redis

import io.vertx.core.Vertx
import io.vertx.ext.auth.VertxContextPRNG
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInput
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import kotlin.random.Random

class RedisSessionCodecImplTest {

    @Test
    fun `a session can be serialized`() {
        val vertx: Vertx = Vertx.vertx()

        val sessionCodec: RedisSessionCodec = RedisSessionCodecImpl()

        val timeout: Long = 10000
        val length = 17
        val session = SharedDataSessionImpl(VertxContextPRNG.current(vertx), timeout, length)

        val result1: ByteArray = sessionCodec.serialize(session = session)
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

        // Insert some data into the session and update other values, then re-serialize.
        val dataKey1 = "test_dataKey1"
        val dataValue1 = "test_dataValue1"
        val dataKey2 = "test_dataKey2"
        val dataValue2 = 2353
        val dataKey3 = "test_dataKey3"
        val dataValue3 = false
        session.put(dataKey1, dataValue1)
        session.put(dataKey2, dataValue2)
        session.put(dataKey3, dataValue3)

        val result2: ByteArray = sessionCodec.serialize(session = session)
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
    fun `a session can be deserialized`() {
        val sessionCodec: RedisSessionCodec = RedisSessionCodecImpl()

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

            val result: SharedDataSessionImpl =
                sessionCodec.deserialize(bytes = serializedSessionOutputStream.toByteArray())
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

            val result: SharedDataSessionImpl =
                sessionCodec.deserialize(bytes = serializedSessionOutputStream.toByteArray())
            assertEquals(id, result.id())
            assertEquals(timeout, result.timeout())
            assertEquals(lastAccessed, result.lastAccessed())
            assertEquals(version, result.version())
            assertFalse(result.isEmpty)
            assertEquals(mapOf(dataKey1 to dataValue1, dataKey2 to dataValue2, dataKey3 to dataValue3), result.data())
        }
    }

    private fun DataInput.readUtf(length: Int): String =
        ByteArray(length).apply { this@readUtf.readFully(this) }.toString(Charsets.UTF_8)
}
