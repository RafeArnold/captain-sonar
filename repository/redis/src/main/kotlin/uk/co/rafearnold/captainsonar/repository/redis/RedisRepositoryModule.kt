package uk.co.rafearnold.captainsonar.repository.redis

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import io.vertx.ext.web.sstore.SessionStore
import uk.co.rafearnold.captainsonar.repository.GameIdRepository
import uk.co.rafearnold.captainsonar.repository.GameRepository
import uk.co.rafearnold.captainsonar.repository.session.SessionEventService

class RedisRepositoryModule : AbstractModule() {

    override fun configure() {
        bind(GameRepository::class.java).to(RedisGameRepository::class.java).`in`(Scopes.SINGLETON)
        bind(RedisSessionCodec::class.java).to(RedisSessionCodecImpl::class.java).`in`(Scopes.SINGLETON)
        bind(SessionStore::class.java).toProvider(RedisSessionStoreProvider::class.java).`in`(Scopes.SINGLETON)
        bind(SessionEventService::class.java).to(RedisSessionEventService::class.java).`in`(Scopes.SINGLETON)
        bind(GameIdRepository::class.java).to(RedisGameIdRepository::class.java).`in`(Scopes.SINGLETON)
    }
}
