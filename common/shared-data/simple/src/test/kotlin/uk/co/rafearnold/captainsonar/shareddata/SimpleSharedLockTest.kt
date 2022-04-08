package uk.co.rafearnold.captainsonar.shareddata

import io.mockk.Ordering
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class SimpleSharedLockTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `lock and unlock delegate to the wrapped instance`() {
        val wrapped: ReentrantLock = spyk(ReentrantLock())
        val lock = SimpleSharedLock(wrapped = wrapped)
        lock.lock()
        verify(ordering = Ordering.SEQUENCE) { wrapped.lock() }
        confirmVerified(wrapped)
        lock.unlock()
        verify(ordering = Ordering.SEQUENCE) {
            wrapped.lock()
            wrapped.unlock()
        }
        confirmVerified(wrapped)
    }

    @Test
    fun `lockInterruptibly delegates to the wrapped instance`() {
        val wrapped: ReentrantLock = spyk(ReentrantLock())
        val lock = SimpleSharedLock(wrapped = wrapped)
        lock.lockInterruptibly()
        verify(ordering = Ordering.SEQUENCE) { wrapped.lockInterruptibly() }
        confirmVerified(wrapped)
    }

    @Test
    fun `tryLock delegates to the wrapped instance`() {
        val wrapped: ReentrantLock = spyk(ReentrantLock())
        val lock = SimpleSharedLock(wrapped = wrapped)
        assertTrue(lock.tryLock())
        verify(ordering = Ordering.SEQUENCE) { wrapped.tryLock() }
        confirmVerified(wrapped)
        assertTrue(lock.tryLock(5, TimeUnit.MILLISECONDS))
        verify(ordering = Ordering.SEQUENCE) {
            wrapped.tryLock()
            wrapped.tryLock(5, TimeUnit.MILLISECONDS)
        }
        confirmVerified(wrapped)
    }

    @Test
    fun `newCondition delegates to the wrapped instance`() {
        val wrapped: ReentrantLock = spyk(ReentrantLock())
        val lock = SimpleSharedLock(wrapped = wrapped)
        lock.newCondition()
        verify(ordering = Ordering.SEQUENCE) { wrapped.newCondition() }
        confirmVerified(wrapped)
    }

    @Test
    fun `isLocked delegates to the wrapped instance`() {
        val wrapped: ReentrantLock = spyk(ReentrantLock())
        val lock = SimpleSharedLock(wrapped = wrapped)
        // TODO: Finish.
//        assertFalse(lock.isLocked)
//        verify(ordering = Ordering.SEQUENCE) { wrapped.isLocked }
        confirmVerified(wrapped)
        lock.lock()
//        assertTrue(lock.isLocked)
        verify(ordering = Ordering.SEQUENCE) {
//            wrapped.isLocked
            wrapped.lock()
//            wrapped.isLocked
        }
        confirmVerified(wrapped)
    }
}
