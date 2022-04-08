package uk.co.rafearnold.captainsonar.shareddata

import io.mockk.clearAllMocks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AbstractSharedDataServiceTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
    }

    @Test
    fun `local longs are stored per instance`() {
        val service1: SharedDataService =
            object : AbstractSharedDataService() {
                override fun getDistributedLong(name: String): SharedAtomicLong =
                    TODO("Not yet implemented")

                override fun getDistributedLock(name: String): SharedLock {
                    TODO("Not yet implemented")
                }

                override fun <K, V> getDistributedMap(name: String): SharedMap<K, V> {
                    TODO("Not yet implemented")
                }
            }
        val service2: SharedDataService =
            object : AbstractSharedDataService() {
                override fun getDistributedLong(name: String): SharedAtomicLong =
                    TODO("Not yet implemented")

                override fun getDistributedLock(name: String): SharedLock {
                    TODO("Not yet implemented")
                }

                override fun <K, V> getDistributedMap(name: String): SharedMap<K, V> {
                    TODO("Not yet implemented")
                }
            }

        val name1 = "test_name1"
        // First two longs should be the same.
        val long1: SharedAtomicLong = service1.getLong(name = name1, localOnly = true)
        val long2: SharedAtomicLong = service1.getLong(name = name1, localOnly = true)
        // This long is retrieved from a different service instance, so should be different.
        val long3: SharedAtomicLong = service2.getLong(name = name1, localOnly = true)
        val name2 = "test_name2"
        // This long has a different name, so should be different.
        val long4: SharedAtomicLong = service1.getLong(name = name2, localOnly = true)

        assertEquals(0, long1.get())
        assertEquals(0, long2.get())
        assertEquals(0, long3.get())
        assertEquals(0, long4.get())

        long1.compareAndSet(0, 1)

        assertEquals(1, long1.get())
        assertEquals(1, long2.get())
        assertEquals(0, long3.get())
        assertEquals(0, long4.get())
    }

    @Test
    fun `local locks are stored per instance`() {
        val service1: SharedDataService =
            object : AbstractSharedDataService() {
                override fun getDistributedLong(name: String): SharedAtomicLong =
                    TODO("Not yet implemented")

                override fun getDistributedLock(name: String): SharedLock {
                    TODO("Not yet implemented")
                }

                override fun <K, V> getDistributedMap(name: String): SharedMap<K, V> {
                    TODO("Not yet implemented")
                }
            }
        val service2: SharedDataService =
            object : AbstractSharedDataService() {
                override fun getDistributedLong(name: String): SharedAtomicLong =
                    TODO("Not yet implemented")

                override fun getDistributedLock(name: String): SharedLock {
                    TODO("Not yet implemented")
                }

                override fun <K, V> getDistributedMap(name: String): SharedMap<K, V> {
                    TODO("Not yet implemented")
                }
            }

        val name1 = "test_name1"
        // First two locks should be the same.
        val lock1: SharedLock = service1.getLock(name = name1, localOnly = true)
        val lock2: SharedLock = service1.getLock(name = name1, localOnly = true)
        // This lock is retrieved from a different service instance, so should be different.
        val lock3: SharedLock = service2.getLock(name = name1, localOnly = true)
        val name2 = "test_name2"
        // This lock has a different name, so should be different.
        val lock4: SharedLock = service1.getLock(name = name2, localOnly = true)

        // TODO: Finish.
        lock1.lock()
//        assertTrue(lock1.isLocked)
//        assertTrue(lock2.isLocked)
//        assertFalse(lock3.isLocked)
//        assertFalse(lock4.isLocked)

        lock1.unlock()
//        assertFalse(lock1.isLocked)
//        assertFalse(lock2.isLocked)
//        assertFalse(lock3.isLocked)
//        assertFalse(lock4.isLocked)

        lock3.lock()
//        assertFalse(lock1.isLocked)
//        assertFalse(lock2.isLocked)
//        assertTrue(lock3.isLocked)
//        assertFalse(lock4.isLocked)

        lock3.unlock()
//        assertFalse(lock1.isLocked)
//        assertFalse(lock2.isLocked)
//        assertFalse(lock3.isLocked)
//        assertFalse(lock4.isLocked)

        lock4.lock()
//        assertFalse(lock1.isLocked)
//        assertFalse(lock2.isLocked)
//        assertFalse(lock3.isLocked)
//        assertTrue(lock4.isLocked)

        lock4.unlock()
//        assertFalse(lock1.isLocked)
//        assertFalse(lock2.isLocked)
//        assertFalse(lock3.isLocked)
//        assertFalse(lock4.isLocked)
    }

    @Test
    fun `local maps are stored per instance`() {
        val service1: SharedDataService =
            object : AbstractSharedDataService() {
                override fun getDistributedLong(name: String): SharedAtomicLong =
                    TODO("Not yet implemented")

                override fun getDistributedLock(name: String): SharedLock {
                    TODO("Not yet implemented")
                }

                override fun <K, V> getDistributedMap(name: String): SharedMap<K, V> {
                    TODO("Not yet implemented")
                }
            }
        val service2: SharedDataService =
            object : AbstractSharedDataService() {
                override fun getDistributedLong(name: String): SharedAtomicLong =
                    TODO("Not yet implemented")

                override fun getDistributedLock(name: String): SharedLock {
                    TODO("Not yet implemented")
                }

                override fun <K, V> getDistributedMap(name: String): SharedMap<K, V> {
                    TODO("Not yet implemented")
                }
            }

        val name1 = "test_name1"
        // First two maps should be the same.
        val map1: MutableMap<String, String> = service1.getMap(name = name1, localOnly = true)
        val map2: MutableMap<String, String> = service1.getMap(name = name1, localOnly = true)
        // This map is retrieved from a different service instance, so should be different.
        val map3: MutableMap<String, String> = service2.getMap(name = name1, localOnly = true)
        val name2 = "test_name2"
        // This map has a different name, so should be different.
        val map4: MutableMap<String, String> = service1.getMap(name = name2, localOnly = true)

        assertEquals(emptyMap<String, String>(), map1)
        assertEquals(emptyMap<String, String>(), map2)
        assertEquals(emptyMap<String, String>(), map3)
        assertEquals(emptyMap<String, String>(), map4)

        val key1 = "test_key1"
        val value1 = "test_value1"
        assertNull(map1.put(key1, value1))

        assertEquals(mapOf(key1 to value1), map1)
        assertEquals(mapOf(key1 to value1), map2)
        assertEquals(emptyMap<String, String>(), map3)
        assertEquals(emptyMap<String, String>(), map4)

        assertEquals(value1, map1.put(key1, value1))

        val key2 = "test_key2"
        val value2 = "test_value2"
        assertNull(map1.put(key2, value2))

        assertEquals(mapOf(key1 to value1, key2 to value2), map1)
        assertEquals(mapOf(key1 to value1, key2 to value2), map2)
        assertEquals(emptyMap<String, String>(), map3)
        assertEquals(emptyMap<String, String>(), map4)

        assertEquals(value1, map1.remove(key1))

        assertEquals(mapOf(key2 to value2), map1)
        assertEquals(mapOf(key2 to value2), map2)
        assertEquals(emptyMap<String, String>(), map3)
        assertEquals(emptyMap<String, String>(), map4)
    }
}
