package uk.co.rafearnold.captainsonar.repository.shareddata

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import io.vertx.ext.web.sstore.SessionStore
import uk.co.rafearnold.captainsonar.common.typeLiteral
import uk.co.rafearnold.captainsonar.repository.GameIdRepository
import uk.co.rafearnold.captainsonar.repository.GameRepository
import uk.co.rafearnold.captainsonar.repository.session.SessionEventService
import uk.co.rafearnold.captainsonar.repository.session.SessionRepositoryModule
import uk.co.rafearnold.captainsonar.shareddata.SharedMap

class SharedDataRepositoryModule : AbstractModule() {

    override fun configure() {
        install(SessionRepositoryModule())
        bind(GameRepository::class.java).to(SharedDataGameRepository::class.java).`in`(Scopes.SINGLETON)
        bind(SessionStore::class.java).toProvider(SharedDataSessionStoreProvider::class.java).`in`(Scopes.SINGLETON)
        bind(SessionEventService::class.java).to(SharedDataSessionEventService::class.java).`in`(Scopes.SINGLETON)
        bind(GameIdRepository::class.java).to(SharedDataGameIdRepository::class.java).`in`(Scopes.SINGLETON)
        bind(typeLiteral<SharedMap<String, ByteArray>>()).annotatedWith(SharedDataSessionStoreData::class.java)
            .toProvider(SharedDataSessionStoreDataProvider::class.java).`in`(Scopes.SINGLETON)
    }
}
