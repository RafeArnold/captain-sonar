package uk.co.rafearnold.captainsonar.shareddata

import com.google.inject.AbstractModule
import com.google.inject.Scopes

class VertxSharedDataModule : AbstractModule() {

    override fun configure() {
        bind(SharedDataService::class.java).to(VertxSharedDataService::class.java).`in`(Scopes.SINGLETON)
    }
}
