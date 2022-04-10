package uk.co.rafearnold.captainsonar.repository.redis

import io.vertx.core.Vertx
import io.vertx.ext.web.sstore.SessionStore
import uk.co.rafearnold.captainsonar.repository.session.SessionStoreFactory
import javax.inject.Inject

class RedisSessionStoreFactory @Inject constructor(
    private val vertx: Vertx,
    private val redisClientProvider: RedisClientProvider,
) : SessionStoreFactory {

    override fun create(): SessionStore =
        RedisSessionStore(vertx = vertx, redisClientProvider = redisClientProvider)
}
