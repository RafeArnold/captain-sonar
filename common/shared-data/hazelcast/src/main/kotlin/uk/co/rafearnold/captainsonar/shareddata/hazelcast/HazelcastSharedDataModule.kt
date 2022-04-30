package uk.co.rafearnold.captainsonar.shareddata.hazelcast

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService

class HazelcastSharedDataModule : AbstractModule() {

    override fun configure() {
        bind(SharedDataService::class.java).to(HazelcastSharedDataService::class.java).`in`(Scopes.SINGLETON)
    }
}
