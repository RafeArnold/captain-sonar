package uk.co.rafearnold.captainsonar.shareddata

import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SharedDataServiceExtensionsTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
    }

    @Test
    fun `a local long can be retrieved`() {
        val service: SharedDataService = mockk()

        val name = "test_name"
        val expectedResult: SharedAtomicLong = mockk()
        every { service.getLong(name = name, localOnly = true) } returns expectedResult

        assertEquals(expectedResult, service.getLocalLong(name = name))
        verify(exactly = 1) {
            service.getLong(name = name, localOnly = true)
        }
        confirmVerified(service)
    }

    @Test
    fun `a distributed long can be retrieved`() {
        val service: SharedDataService = mockk()

        val name = "test_name"
        val expectedResult: SharedAtomicLong = mockk()
        every { service.getLong(name = name, localOnly = false) } returns expectedResult

        assertEquals(expectedResult, service.getDistributedLong(name = name))
        verify(exactly = 1) {
            service.getLong(name = name, localOnly = false)
        }
        confirmVerified(service)
    }

    @Test
    fun `a local lock can be retrieved`() {
        val service: SharedDataService = mockk()

        val name = "test_name"
        val expectedResult: SharedLock = mockk()
        every { service.getLock(name = name, localOnly = true) } returns expectedResult

        assertEquals(expectedResult, service.getLocalLock(name = name))
        verify(exactly = 1) {
            service.getLock(name = name, localOnly = true)
        }
        confirmVerified(service)
    }

    @Test
    fun `a distributed lock can be retrieved`() {
        val service: SharedDataService = mockk()

        val name = "test_name"
        val expectedResult: SharedLock = mockk()
        every { service.getLock(name = name, localOnly = false) } returns expectedResult

        assertEquals(expectedResult, service.getDistributedLock(name = name))
        verify(exactly = 1) {
            service.getLock(name = name, localOnly = false)
        }
        confirmVerified(service)
    }

    @Test
    fun `a local map can be retrieved`() {
        val service: SharedDataService = mockk()

        val name = "test_name"
        val expectedResult: SharedMap<String, String> = mockk()
        every { service.getMap<String, String>(name = name, localOnly = true) } returns expectedResult

        assertEquals(expectedResult, service.getLocalMap<String, String>(name = name))
        verify(exactly = 1) {
            service.getMap<String, String>(name = name, localOnly = true)
        }
        confirmVerified(service)
    }

    @Test
    fun `a distributed map can be retrieved`() {
        val service: SharedDataService = mockk()

        val name = "test_name"
        val expectedResult: SharedMap<String, String> = mockk()
        every { service.getMap<String, String>(name = name, localOnly = false) } returns expectedResult

        assertEquals(expectedResult, service.getDistributedMap<String, String>(name = name))
        verify(exactly = 1) {
            service.getMap<String, String>(name = name, localOnly = false)
        }
        confirmVerified(service)
    }
}
