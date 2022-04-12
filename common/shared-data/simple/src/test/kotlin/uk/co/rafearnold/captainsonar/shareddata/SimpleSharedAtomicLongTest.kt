package uk.co.rafearnold.captainsonar.shareddata

import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.spyk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicLong

class SimpleSharedAtomicLongTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
    }

    @Test
    fun `get delegates to the wrapped value`() {
        val wrapped: AtomicLong = spyk(AtomicLong(7))
        val wrapper = SimpleSharedAtomicLong(wrappedLong = wrapped)

        assertEquals(7, wrapper.get())
        verify(exactly = 1) {
            wrapped.get()
        }
        confirmVerified(wrapped)
    }

    @Test
    fun `compareAndSet delegates to the wrapped value`() {
        val wrapped: AtomicLong = spyk(AtomicLong(7))
        val wrapper = SimpleSharedAtomicLong(wrappedLong = wrapped)

        assertTrue(wrapper.compareAndSet(7, 3))
        verify(exactly = 1) {
            wrapped.compareAndSet(7, 3)
        }
        confirmVerified(wrapped)
        assertEquals(3, wrapper.get())

        clearMocks(wrapped)

        assertFalse(wrapper.compareAndSet(7, 3))
        verify(exactly = 1) {
            wrapped.compareAndSet(7, 3)
        }
        confirmVerified(wrapped)
        assertEquals(3, wrapper.get())
    }

    @Test
    fun `getAndIncrement delegates to the wrapped value`() {
        val wrapped: AtomicLong = spyk(AtomicLong(7))
        val wrapper = SimpleSharedAtomicLong(wrappedLong = wrapped)

        assertEquals(7, wrapper.getAndIncrement())
        verify(exactly = 1) {
            wrapped.getAndIncrement()
        }
        confirmVerified(wrapped)
        assertEquals(8, wrapper.get())

        clearMocks(wrapped)

        assertEquals(8, wrapper.getAndIncrement())
        verify(exactly = 1) {
            wrapped.getAndIncrement()
        }
        confirmVerified(wrapped)
        assertEquals(9, wrapper.get())
    }
}
