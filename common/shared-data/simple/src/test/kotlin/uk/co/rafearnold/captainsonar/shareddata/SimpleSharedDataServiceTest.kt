package uk.co.rafearnold.captainsonar.shareddata

import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SimpleSharedDataServiceTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
        SimpleClusterManager.clearAllClusters()
    }

    @Test
    fun `distributed longs are shared across the same cluster id`() {
        val clusterId1 = "test_clusterId1"
        val clusterId2 = "test_clusterId2"
        val service1: SharedDataService = SimpleClusterManager.createSharedDataService(clusterId = clusterId1)
        val service2: SharedDataService = SimpleClusterManager.createSharedDataService(clusterId = clusterId1)
        val service3: SharedDataService = SimpleClusterManager.createSharedDataService(clusterId = clusterId2)
        val service4: SharedDataService = SimpleClusterManager.createSharedDataService(clusterId = clusterId2)

        val name1 = "test_name1"
        val name2 = "test_name2"

        val long1: SharedAtomicLong = service1.getLong(name = name1, localOnly = false)
        val long2: SharedAtomicLong = service2.getLong(name = name1, localOnly = false)
        val long3: SharedAtomicLong = service1.getLong(name = name2, localOnly = false)
        val long4: SharedAtomicLong = service2.getLong(name = name2, localOnly = false)
        val long5: SharedAtomicLong = service3.getLong(name = name1, localOnly = false)
        val long6: SharedAtomicLong = service4.getLong(name = name1, localOnly = false)
        val long7: SharedAtomicLong = service3.getLong(name = name2, localOnly = false)
        val long8: SharedAtomicLong = service4.getLong(name = name2, localOnly = false)

        assertEquals(0, long1.get())
        assertEquals(0, long2.get())
        assertEquals(0, long3.get())
        assertEquals(0, long4.get())
        assertEquals(0, long5.get())
        assertEquals(0, long6.get())
        assertEquals(0, long7.get())
        assertEquals(0, long8.get())

        long1.compareAndSet(0, 4)

        assertEquals(4, long1.get())
        assertEquals(4, long2.get())
        assertEquals(0, long3.get())
        assertEquals(0, long4.get())
        assertEquals(0, long5.get())
        assertEquals(0, long6.get())
        assertEquals(0, long7.get())
        assertEquals(0, long8.get())

        long2.compareAndSet(4, 5)
        long5.compareAndSet(0, 235)
        long8.compareAndSet(0, 59)

        assertEquals(5, long1.get())
        assertEquals(5, long2.get())
        assertEquals(0, long3.get())
        assertEquals(0, long4.get())
        assertEquals(235, long5.get())
        assertEquals(235, long6.get())
        assertEquals(59, long7.get())
        assertEquals(59, long8.get())

        val long9: SharedAtomicLong = service3.getLong(name = name1, localOnly = false)
        val long10: SharedAtomicLong = service4.getLong(name = name1, localOnly = false)

        assertEquals(5, long1.get())
        assertEquals(5, long2.get())
        assertEquals(0, long3.get())
        assertEquals(0, long4.get())
        assertEquals(235, long5.get())
        assertEquals(235, long6.get())
        assertEquals(59, long7.get())
        assertEquals(59, long8.get())
        assertEquals(235, long9.get())
        assertEquals(235, long10.get())
    }

    @Test
    fun `distributed locks are shared across the same cluster id`() {
        val clusterId1 = "test_clusterId1"
        val clusterId2 = "test_clusterId2"
        val service1: SharedDataService = SimpleClusterManager.createSharedDataService(clusterId = clusterId1)
        val service2: SharedDataService = SimpleClusterManager.createSharedDataService(clusterId = clusterId1)
        val service3: SharedDataService = SimpleClusterManager.createSharedDataService(clusterId = clusterId2)
        val service4: SharedDataService = SimpleClusterManager.createSharedDataService(clusterId = clusterId2)

        val name1 = "test_name1"
        val name2 = "test_name2"

        val lock1: SharedLock = service1.getLock(name = name1, localOnly = false)
        val lock2: SharedLock = service2.getLock(name = name1, localOnly = false)
        val lock3: SharedLock = service1.getLock(name = name2, localOnly = false)
        val lock4: SharedLock = service2.getLock(name = name2, localOnly = false)
        val lock5: SharedLock = service3.getLock(name = name1, localOnly = false)
        val lock6: SharedLock = service4.getLock(name = name1, localOnly = false)
        val lock7: SharedLock = service3.getLock(name = name2, localOnly = false)
        val lock8: SharedLock = service4.getLock(name = name2, localOnly = false)

        // TODO: Finish.
//        assertFalse(lock1.isLocked)
//        assertFalse(lock2.isLocked)
//        assertFalse(lock3.isLocked)
//        assertFalse(lock4.isLocked)
//        assertFalse(lock5.isLocked)
//        assertFalse(lock6.isLocked)
//        assertFalse(lock7.isLocked)
//        assertFalse(lock8.isLocked)

        lock1.lock()

//        assertTrue(lock1.isLocked)
//        assertTrue(lock2.isLocked)
//        assertFalse(lock3.isLocked)
//        assertFalse(lock4.isLocked)
//        assertFalse(lock5.isLocked)
//        assertFalse(lock6.isLocked)
//        assertFalse(lock7.isLocked)
//        assertFalse(lock8.isLocked)

        lock2.unlock()
        lock5.lock()
        lock8.lock()

//        assertFalse(lock1.isLocked)
//        assertFalse(lock2.isLocked)
//        assertFalse(lock3.isLocked)
//        assertFalse(lock4.isLocked)
//        assertTrue(lock5.isLocked)
//        assertTrue(lock6.isLocked)
//        assertTrue(lock7.isLocked)
//        assertTrue(lock8.isLocked)

        val lock9: SharedLock = service2.getLock(name = name1, localOnly = false)
        val lock10: SharedLock = service4.getLock(name = name1, localOnly = false)

//        assertFalse(lock1.isLocked)
//        assertFalse(lock2.isLocked)
//        assertFalse(lock3.isLocked)
//        assertFalse(lock4.isLocked)
//        assertTrue(lock5.isLocked)
//        assertTrue(lock6.isLocked)
//        assertTrue(lock7.isLocked)
//        assertTrue(lock8.isLocked)
//        assertFalse(lock9.isLocked)
//        assertTrue(lock10.isLocked)
    }

    @Test
    fun `distributed maps are shared across the same cluster id`() {
        val clusterId1 = "test_clusterId1"
        val clusterId2 = "test_clusterId2"
        val service1: SharedDataService = SimpleClusterManager.createSharedDataService(clusterId = clusterId1)
        val service2: SharedDataService = SimpleClusterManager.createSharedDataService(clusterId = clusterId1)
        val service3: SharedDataService = SimpleClusterManager.createSharedDataService(clusterId = clusterId2)
        val service4: SharedDataService = SimpleClusterManager.createSharedDataService(clusterId = clusterId2)

        val name1 = "test_name1"
        val name2 = "test_name2"

        val map1: MutableMap<String, String> = service1.getMap(name = name1, localOnly = false)
        val map2: MutableMap<String, String> = service2.getMap(name = name1, localOnly = false)
        val map3: MutableMap<String, String> = service1.getMap(name = name2, localOnly = false)
        val map4: MutableMap<String, String> = service2.getMap(name = name2, localOnly = false)
        val map5: MutableMap<String, String> = service3.getMap(name = name1, localOnly = false)
        val map6: MutableMap<String, String> = service4.getMap(name = name1, localOnly = false)
        val map7: MutableMap<String, String> = service3.getMap(name = name2, localOnly = false)
        val map8: MutableMap<String, String> = service4.getMap(name = name2, localOnly = false)

        assertEquals(emptyMap<String, String>(), map1)
        assertEquals(emptyMap<String, String>(), map2)
        assertEquals(emptyMap<String, String>(), map3)
        assertEquals(emptyMap<String, String>(), map4)
        assertEquals(emptyMap<String, String>(), map5)
        assertEquals(emptyMap<String, String>(), map6)
        assertEquals(emptyMap<String, String>(), map7)
        assertEquals(emptyMap<String, String>(), map8)

        val key1 = "test_key1"
        val value1 = "test_value1"
        assertNull(map1.put(key1, value1))

        assertEquals(mapOf(key1 to value1), map1)
        assertEquals(mapOf(key1 to value1), map2)
        assertEquals(emptyMap<String, String>(), map3)
        assertEquals(emptyMap<String, String>(), map4)
        assertEquals(emptyMap<String, String>(), map5)
        assertEquals(emptyMap<String, String>(), map6)
        assertEquals(emptyMap<String, String>(), map7)
        assertEquals(emptyMap<String, String>(), map8)

        assertEquals(value1, map2.put(key1, value1))

        val key2 = "test_key2"
        val value2 = "test_value2"
        assertNull(map1.put(key2, value2))
        assertNull(map6.put(key2, value2))

        assertEquals(mapOf(key1 to value1, key2 to value2), map1)
        assertEquals(mapOf(key1 to value1, key2 to value2), map2)
        assertEquals(emptyMap<String, String>(), map3)
        assertEquals(emptyMap<String, String>(), map4)
        assertEquals(mapOf(key2 to value2), map5)
        assertEquals(mapOf(key2 to value2), map6)
        assertEquals(emptyMap<String, String>(), map7)
        assertEquals(emptyMap<String, String>(), map8)

        assertEquals(value1, map2.remove(key1))
        assertNull(map3.put(key1, value1))

        val map9: MutableMap<String, String> = service3.getMap(name = name1, localOnly = false)
        val map10: MutableMap<String, String> = service4.getMap(name = name1, localOnly = false)

        assertEquals(mapOf(key2 to value2), map1)
        assertEquals(mapOf(key2 to value2), map2)
        assertEquals(mapOf(key1 to value1), map3)
        assertEquals(mapOf(key1 to value1), map4)
        assertEquals(mapOf(key2 to value2), map5)
        assertEquals(mapOf(key2 to value2), map6)
        assertEquals(emptyMap<String, String>(), map7)
        assertEquals(emptyMap<String, String>(), map8)
        assertEquals(mapOf(key2 to value2), map9)
        assertEquals(mapOf(key2 to value2), map10)
    }
}
