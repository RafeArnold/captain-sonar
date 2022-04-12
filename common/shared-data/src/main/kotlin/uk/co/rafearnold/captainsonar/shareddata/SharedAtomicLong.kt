package uk.co.rafearnold.captainsonar.shareddata

interface SharedAtomicLong {

    fun get(): Long

    fun compareAndSet(expectValue: Long, newValue: Long): Boolean

    fun getAndIncrement(): Long
}
