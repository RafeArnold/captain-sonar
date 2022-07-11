package uk.co.rafearnold.captainsonar.repository.redis

import io.vertx.core.Vertx
import io.vertx.ext.web.sstore.SessionStore
import uk.co.rafearnold.commons.shareddata.SharedDataService
import javax.inject.Inject
import javax.inject.Provider

class RedisSessionStoreProvider @Inject constructor(
    vertx: Vertx,
    redisClientProvider: RedisClientProvider,
    sessionCodec: RedisSessionCodec,
    sharedDataService: SharedDataService,
) : Provider<SessionStore> {

    private val sessionStore: SessionStore =
        RedisSessionStore(
            vertx = vertx,
            redisClientProvider = redisClientProvider,
            sessionCodec = sessionCodec,
            sharedDataService = sharedDataService
        )

    override fun get(): SessionStore = sessionStore
}
