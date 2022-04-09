package uk.co.rafearnold.captainsonar.repository

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import uk.co.rafearnold.captainsonar.repository.session.SessionStoreFactory

class RedisRepositoryModule : AbstractModule() {

    override fun configure() {
        bind(GameRepository::class.java).to(RedisGameRepository::class.java).`in`(Scopes.SINGLETON)
        bind(SessionStoreFactory::class.java).to(RedisSessionStoreFactory::class.java).`in`(Scopes.SINGLETON)
    }
}
