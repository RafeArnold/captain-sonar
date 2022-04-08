package uk.co.rafearnold.captainsonar.shareddata

import com.hazelcast.cp.IAtomicLong

class HazelcastSharedAtomicLong(private val hazelcastLong: IAtomicLong) : SharedAtomicLong {

    override fun get(): Long = hazelcastLong.get()

    override fun compareAndSet(expectValue: Long, newValue: Long): Boolean =
        hazelcastLong.compareAndSet(expectValue, newValue)
}