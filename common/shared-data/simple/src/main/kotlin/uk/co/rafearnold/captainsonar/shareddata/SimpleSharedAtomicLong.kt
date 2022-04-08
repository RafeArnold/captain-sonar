package uk.co.rafearnold.captainsonar.shareddata

import java.util.concurrent.atomic.AtomicLong

/**
 * An implementation of [SharedAtomicLong] that wraps an [AtomicLong].
 */
class SimpleSharedAtomicLong(
    private val wrappedLong: AtomicLong
) : SharedAtomicLong {

    override fun get(): Long = wrappedLong.get()

    override fun compareAndSet(expectValue: Long, newValue: Long): Boolean =
        wrappedLong.compareAndSet(expectValue, newValue)
}
