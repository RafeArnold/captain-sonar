package uk.co.rafearnold.captainsonar.shareddata

import com.google.inject.AbstractModule
import com.google.inject.Scopes

class HazelcastSharedDataModule : AbstractModule() {

    override fun configure() {
        bind(SharedDataService::class.java).to(HazelcastSharedDataService::class.java).`in`(Scopes.SINGLETON)
    }
}
