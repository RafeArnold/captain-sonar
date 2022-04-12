package uk.co.rafearnold.captainsonar.repository.shareddata

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import uk.co.rafearnold.captainsonar.repository.GameIdRepository
import uk.co.rafearnold.captainsonar.repository.GameRepository
import uk.co.rafearnold.captainsonar.repository.session.SessionStoreFactory

class SharedDataRepositoryModule : AbstractModule() {

    override fun configure() {
        bind(GameRepository::class.java).to(SharedDataGameRepository::class.java).`in`(Scopes.SINGLETON)
        bind(SessionStoreFactory::class.java).to(SharedDataSessionStoreFactory::class.java).`in`(Scopes.SINGLETON)
        bind(GameIdRepository::class.java).to(SharedDataGameIdRepository::class.java).`in`(Scopes.SINGLETON)
    }
}
