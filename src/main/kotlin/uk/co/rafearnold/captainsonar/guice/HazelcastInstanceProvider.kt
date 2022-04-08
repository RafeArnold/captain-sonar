package uk.co.rafearnold.captainsonar.guice

import com.hazelcast.core.Hazelcast
import com.hazelcast.core.HazelcastInstance
import javax.inject.Provider

class HazelcastInstanceProvider : Provider<HazelcastInstance> {

    override fun get(): HazelcastInstance = Hazelcast.getAllHazelcastInstances().first()
}
