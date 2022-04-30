package uk.co.rafearnold.captainsonar.shareddata

import io.mockk.clearAllMocks
import io.mockk.clearConstructorMockk
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import uk.co.rafearnold.captainsonar.shareddata.simple.SimpleSharedMap
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class SimpleSharedMapTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `put delegates to the wrapped map`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        val key1 = "test_key1"
        val value1 = "test_value1"

        assertNull(map.put(key = key1, value = value1))
        verify(exactly = 1) {
            wrapped[key1] = value1
        }
        assertEquals(mapOf(key1 to value1), wrapped)
        assertEquals(mapOf(key1 to value1), map)

        clearMocks(wrapped)

        val value2 = "test_value2"

        assertEquals(value1, map.put(key = key1, value = value2))
        verify(exactly = 1) {
            wrapped[key1] = value2
        }
        assertEquals(mapOf(key1 to value2), wrapped)
        assertEquals(mapOf(key1 to value2), map)

        clearMocks(wrapped)

        val key2 = "test_key2"

        assertNull(map.put(key = key2, value = value1))
        verify(exactly = 1) {
            wrapped[key2] = value1
        }
        assertEquals(mapOf(key1 to value2, key2 to value1), wrapped)
        assertEquals(mapOf(key1 to value2, key2 to value1), map)
    }

    @Test
    fun `ttl can be specified when putting a key`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        val key = "test_key"
        val value = "test_value"

        assertNull(map.put(key = key, value = value, ttl = 100, ttlUnit = TimeUnit.MILLISECONDS))
        verify(exactly = 1) {
            wrapped[key] = value
        }
        assertEquals(mapOf(key to value), wrapped)
        assertEquals(mapOf(key to value), map)
        Thread.sleep(150)
        verify(exactly = 1) {
            wrapped.remove(key = key)
        }
        assertEquals(mapOf<String, String>(), wrapped)
        assertEquals(mapOf<String, String>(), map)
    }

    @Test
    fun `when a key is put without specifying a ttl then the default is used`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        map.defaultTtlMillis = 100

        val key = "test_key"
        val value = "test_value"

        assertNull(map.put(key = key, value = value))
        verify(exactly = 1) {
            wrapped[key] = value
        }
        Thread.sleep(150)
        verify(exactly = 1) {
            wrapped.remove(key = key)
        }
    }

    @Test
    fun `when a key is put with a negative ttl then the default is used`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        map.defaultTtlMillis = 100

        val key1 = "test_key1"
        val key2 = "test_key2"
        val key3 = "test_key3"
        val value = "test_value"

        assertNull(map.put(key = key1, value = value, -1, TimeUnit.SECONDS))
        assertNull(map.put(key = key2, value = value, -312, TimeUnit.DAYS))
        assertNull(map.put(key = key3, value = value, 0, TimeUnit.SECONDS))
        verify(exactly = 1) {
            wrapped[key1] = value
            wrapped[key2] = value
            wrapped[key3] = value
        }
        Thread.sleep(150)
        verify(exactly = 1) {
            wrapped.remove(key = key1)
            wrapped.remove(key = key2)
        }
        verify(inverse = true) {
            // Verify the key with a non-negative TTL has not been removed.
            wrapped.remove(key = key3)
        }
    }

    @Test
    fun `when a key is put with a ttl of zero then no ttl is set`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        map.defaultTtlMillis = 100

        val key = "test_key"
        val value = "test_value"

        assertNull(map.put(key = key, value = value, ttl = 0, ttlUnit = TimeUnit.MILLISECONDS))
        verify(exactly = 1) {
            wrapped[key] = value
        }
        assertEquals(mapOf(key to value), map)
        Thread.sleep(150)
        verify(inverse = true) {
            // Verify the key has not been removed after the default TTL amount.
            wrapped.remove(key = key)
        }
        assertEquals(mapOf(key to value), map)
    }

    @Test
    fun `putIfAbsent delegates to the wrapped map`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        val key1 = "test_key1"
        val value1 = "test_value1"

        assertNull(map.putIfAbsent(key1, value1))
        verify(exactly = 1) {
            wrapped.putIfAbsent(key1, value1)
        }
        assertEquals(mapOf(key1 to value1), wrapped)
        assertEquals(mapOf(key1 to value1), map)

        clearMocks(wrapped)

        val value2 = "test_value2"

        assertEquals(value1, map.putIfAbsent(key1, value2))
        verify(exactly = 1) {
            wrapped.putIfAbsent(key1, value2)
        }
        assertEquals(mapOf(key1 to value1), wrapped)
        assertEquals(mapOf(key1 to value1), map)

        clearMocks(wrapped)

        val key2 = "test_key2"

        assertNull(map.putIfAbsent(key2, value1))
        verify(exactly = 1) {
            wrapped.putIfAbsent(key2, value1)
        }
        assertEquals(mapOf(key1 to value1, key2 to value1), wrapped)
        assertEquals(mapOf(key1 to value1, key2 to value1), map)
    }

    @Test
    fun `ttl can be specified when putting a key if absent`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        val key = "test_key"
        val value = "test_value"

        assertNull(map.putIfAbsent(key = key, value = value, ttl = 200, ttlUnit = TimeUnit.MILLISECONDS))
        verify(exactly = 1) {
            wrapped.putIfAbsent(key, value)
        }
        assertEquals(mapOf(key to value), wrapped)
        assertEquals(mapOf(key to value), map)
        Thread.sleep(250)
        verify(exactly = 1) {
            wrapped.remove(key = key)
        }
        assertEquals(mapOf<String, String>(), wrapped)
        assertEquals(mapOf<String, String>(), map)
    }

    @Test
    fun `when a key is putIfAbsent without specifying a ttl then the default is used`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        map.defaultTtlMillis = 100

        val key = "test_key"
        val value = "test_value"

        assertNull(map.putIfAbsent(key, value))
        verify(exactly = 1) {
            wrapped.putIfAbsent(key, value)
        }
        Thread.sleep(150)
        verify(exactly = 1) {
            wrapped.remove(key = key)
        }
    }

    @Test
    fun `when a key is putIfAbsent with a negative ttl then the default is used`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        map.defaultTtlMillis = 100

        val key1 = "test_key1"
        val key2 = "test_key2"
        val key3 = "test_key3"
        val value = "test_value"

        assertNull(map.putIfAbsent(key = key1, value = value, -1, TimeUnit.SECONDS))
        assertNull(map.putIfAbsent(key = key2, value = value, -312, TimeUnit.DAYS))
        assertNull(map.putIfAbsent(key = key3, value = value, 0, TimeUnit.SECONDS))
        verify(exactly = 1) {
            wrapped.putIfAbsent(key1, value)
            wrapped.putIfAbsent(key2, value)
            wrapped.putIfAbsent(key3, value)
        }
        Thread.sleep(150)
        verify(exactly = 1) {
            wrapped.remove(key = key1)
            wrapped.remove(key = key2)
        }
        verify(inverse = true) {
            // Verify the key with a non-negative TTL has not been removed.
            wrapped.remove(key = key3)
        }
    }

    @Test
    fun `when a key is putIfAbsent with a ttl of zero then no ttl is set`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        map.defaultTtlMillis = 100

        val key = "test_key"
        val value = "test_value"

        assertNull(map.putIfAbsent(key = key, value = value, ttl = 0, ttlUnit = TimeUnit.MILLISECONDS))
        verify(exactly = 1) {
            wrapped.putIfAbsent(key, value)
        }
        assertEquals(mapOf(key to value), map)
        Thread.sleep(150)
        verify(inverse = true) {
            // Verify the key has not been removed after the default TTL amount.
            wrapped.remove(key = key)
        }
        assertEquals(mapOf(key to value), map)
    }

    @Test
    fun `when the map already contains a key then putIfAbsent does not affect the ttl of the existing key`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        val key = "test_key"
        val value = "test_value"

        assertNull(map.put(key = key, value = value, ttl = 200, ttlUnit = TimeUnit.MILLISECONDS))
        verify(exactly = 1) {
            wrapped[key] = value
        }
        Thread.sleep(100)
        clearMocks(wrapped)
        // Try to add the same key again.
        assertEquals(value, map.putIfAbsent(key = key, value = value, ttl = 1000, ttlUnit = TimeUnit.MILLISECONDS))
        Thread.sleep(150)
        verify(exactly = 1) {
            wrapped.putIfAbsent(key, value)
            // Verify the key was removed after the original TTL amount.
            wrapped.remove(key = key)
        }
    }

    @Test
    fun `when the default ttl is set to a negative value then an exception is thrown`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        assertThrows<IllegalArgumentException> { map.defaultTtlMillis = -1 }
        assertThrows<IllegalArgumentException> { map.defaultTtlMillis = -500 }
        assertDoesNotThrow { map.defaultTtlMillis = 0 }
        assertDoesNotThrow { map.defaultTtlMillis = 1 }
        assertDoesNotThrow { map.defaultTtlMillis = 500 }
    }

    @Test
    fun `when the default ttl is set to zero then no ttl is set by default`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        val key1 = "test_key1"
        val key2 = "test_key2"
        val value = "test_value"

        mockkConstructor(Timer::class)
        every { anyConstructed<Timer>().schedule(any(), any<Long>()) } answers { callOriginal() }

        // Set the default to non-zero initially to confirm a timer is scheduled.
        map.defaultTtlMillis = 100

        assertNull(map.put(key = key1, value = value))
        verify(exactly = 1) {
            anyConstructed<Timer>().schedule(any(), 100)
        }

        clearConstructorMockk(Timer::class)
        every { anyConstructed<Timer>().schedule(any(), any<Long>()) } answers { callOriginal() }

        // Now set the default to zero.
        map.defaultTtlMillis = 0

        assertNull(map.put(key = key2, value = value))
        verify(inverse = true) {
            // Verify no timers have been scheduled.
            anyConstructed<Timer>().schedule(any(), any<Long>())
            anyConstructed<Timer>().schedule(any(), any<Date>())
            anyConstructed<Timer>().schedule(any(), any<Long>(), any())
            anyConstructed<Timer>().schedule(any(), any<Date>(), any())
        }
    }

    @Test
    fun `when the ttl is set to less than a millisecond then it is rounded up to 1 millisecond`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        val key = "test_key"
        val value = "test_value"

        assertNull(map.put(key = key, value = value, ttl = 1, ttlUnit = TimeUnit.NANOSECONDS))
        verify(exactly = 1) {
            wrapped[key] = value
        }
        Thread.sleep(10)
        verify(exactly = 1) {
            wrapped.remove(key = key)
        }
    }

    @Test
    fun `when putting another map then the default ttl is used for each key`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = spyk(SimpleSharedMap(wrappedMap = wrapped))

        val key1 = "test_key1"
        val key2 = "test_key2"
        val key3 = "test_key3"
        val value = "test_value"
        val otherMap: Map<String, String> = mapOf(key1 to value, key2 to value, key3 to value)

        map.putAll(from = otherMap)
        verify(exactly = 1) {
            map.putAll(from = otherMap)
            map[key1] = value
            wrapped[key1] = value
            map[key2] = value
            wrapped[key2] = value
            map[key3] = value
            wrapped[key3] = value
        }
    }

    @Test
    fun `when an entry is removed then its ttl timer is also cancelled and removed`() {
        val wrapped: MutableMap<String, String> = spyk(mutableMapOf())
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        mockkConstructor(Timer::class)
        every { anyConstructed<Timer>().cancel() } answers { callOriginal() }

        val key1 = "test_key1"
        val key2 = "test_key2"
        val value1 = "test_value1"
        val value2 = "test_value2"
        val value3 = "test_value3"

        // Add two entries with the same TTL.
        map.put(key = key1, value = value1, ttl = 100, ttlUnit = TimeUnit.MILLISECONDS)
        map.put(key = key2, value = value2, ttl = 100, ttlUnit = TimeUnit.MILLISECONDS)
        assertEquals(mapOf(key1 to value1, key2 to value2), map)
        assertEquals(mapOf(key1 to value1, key2 to value2), wrapped)

        // Remove the first entry.
        assertEquals(value1, map.remove(key = key1))
        // Check that a timer was cancelled. We'll have to assume this is the first entry's TTL timer.
        verify(exactly = 1) {
            anyConstructed<Timer>().cancel()
        }

        // Re-add the first entry with no TTL.
        assertNull(map.put(key = key1, value = value3, ttl = 0, ttlUnit = TimeUnit.MILLISECONDS))

        // Wait the original TTL amount.
        Thread.sleep(150)

        // Check the second entry has been removed, but not first entry.
        assertEquals(mapOf(key1 to value3), map)
        assertEquals(mapOf(key1 to value3), wrapped)
    }

    @Test
    fun `when the map is cleared then its ttl timers are all cancelled and removed`() {
        val wrapped: MutableMap<String, String> = ConcurrentHashMap()
        val map: SharedMap<String, String> = SimpleSharedMap(wrappedMap = wrapped)

        mockkConstructor(Timer::class)
        every { anyConstructed<Timer>().cancel() } answers { callOriginal() }

        val key1 = "test_key1"
        val key2 = "test_key2"
        val key3 = "test_key3"
        val value1 = "test_value1"
        val value2 = "test_value2"
        val value3 = "test_value3"

        map.put(key = key1, value = value1, ttl = 100, ttlUnit = TimeUnit.MILLISECONDS)
        map.put(key = key2, value = value2, ttl = 5, ttlUnit = TimeUnit.DAYS)
        map.put(key = key3, value = value3, ttl = 0, ttlUnit = TimeUnit.MILLISECONDS)
        assertEquals(mapOf(key1 to value1, key2 to value2, key3 to value3), map)
        assertEquals(mapOf(key1 to value1, key2 to value2, key3 to value3), wrapped)

        // Clear the map.
        map.clear()
        // Check that timers were cancelled.
        verify(exactly = 2) {
            anyConstructed<Timer>().cancel()
        }
        // Check the maps have been cleared.
        assertEquals(mapOf<String, String>(), map)
        assertEquals(mapOf<String, String>(), wrapped)

        // Re-add the first entry with no TTL.
        assertNull(map.put(key = key1, value = value3, ttl = 0, ttlUnit = TimeUnit.MILLISECONDS))

        // Wait the original TTL amount.
        Thread.sleep(100)

        // Check the re-added entry has not been removed.
        assertEquals(mapOf(key1 to value3), map)
        assertEquals(mapOf(key1 to value3), wrapped)
    }

    @Test
    @Suppress("ControlFlowWithEmptyBody")
    fun `listeners can be added and removed from the map`() {
        val map: SharedMap<String, String> = SimpleSharedMap()

        val key1 = "test_key1"
        val key2 = "test_key2"
        val key3 = "test_key3"
        val value1 = "test_value1"
        val value2 = "test_value2"
        val value3 = "test_value3"

        map[key1] = value1

        val listener1Events: Queue<SharedMapEvent<String, String>> = ConcurrentLinkedQueue()
        val listener1Id = map.addListener { listener1Events.add(it) }

        map[key1] = value2

        CompletableFuture.runAsync { while (listener1Events.size != 1); }.get(2, TimeUnit.SECONDS)
        assertEquals(1, listener1Events.size)
        assertEquals(EntryUpdatedEvent(key = key1, oldValue = value1, newValue = value2), listener1Events.poll())

        val listener2Events: Queue<SharedMapEvent<String, String>> = ConcurrentLinkedQueue()
        map.addListener { listener2Events.add(it) }

        map[key2] = value1

        CompletableFuture.runAsync { while (listener1Events.size != 1); }.get(2, TimeUnit.SECONDS)
        assertEquals(1, listener1Events.size)
        assertEquals(EntryAddedEvent(key = key2, newValue = value1), listener1Events.poll())
        CompletableFuture.runAsync { while (listener2Events.size != 1); }.get(2, TimeUnit.SECONDS)
        assertEquals(1, listener2Events.size)
        assertEquals(EntryAddedEvent(key = key2, newValue = value1), listener2Events.poll())

        val ttlMs: Long = 10
        map.put(key = key3, value = value3, ttl = ttlMs, ttlUnit = TimeUnit.MILLISECONDS)

        Thread.sleep(ttlMs)
        CompletableFuture.runAsync { while (listener1Events.size != 2); }.get(2, TimeUnit.SECONDS)
        assertEquals(2, listener1Events.size)
        assertEquals(EntryAddedEvent(key = key3, newValue = value3), listener1Events.poll())
        assertEquals(EntryExpiredEvent(key = key3, oldValue = value3), listener1Events.poll())
        CompletableFuture.runAsync { while (listener2Events.size != 2); }.get(2, TimeUnit.SECONDS)
        assertEquals(2, listener2Events.size)
        assertEquals(EntryAddedEvent(key = key3, newValue = value3), listener2Events.poll())
        assertEquals(EntryExpiredEvent(key = key3, oldValue = value3), listener2Events.poll())

        map.removeListener(listenerId = listener1Id)

        map.remove(key1)

        CompletableFuture.runAsync { while (listener2Events.size != 1); }.get(2, TimeUnit.SECONDS)
        assertEquals(1, listener2Events.size)
        assertEquals(EntryRemovedEvent(key = key1, oldValue = value2), listener2Events.poll())
        assertEquals(0, listener1Events.size)
    }
}
