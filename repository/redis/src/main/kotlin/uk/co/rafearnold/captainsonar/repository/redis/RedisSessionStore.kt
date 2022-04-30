package uk.co.rafearnold.captainsonar.repository.redis

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.VertxContextPRNG
import io.vertx.ext.web.Session
import io.vertx.ext.web.sstore.SessionStore
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import uk.co.rafearnold.captainsonar.shareddata.SharedLock
import uk.co.rafearnold.captainsonar.shareddata.getDistributedLock
import uk.co.rafearnold.captainsonar.shareddata.withLock

/**
 * Responsibility of session storage is shared between this class and [RedisSessionEventService].
 */
internal class RedisSessionStore(
    vertx: Vertx,
    private val redisClientProvider: RedisClientProvider,
    private val sessionCodec: RedisSessionCodec,
    sharedDataService: SharedDataService,
) : SessionStore {

    private val lock: SharedLock =
        sharedDataService.getDistributedLock("uk.co.rafearnold.captainsonar.repository.session-store.lock")

    private val redisClient: Jedis get() = redisClientProvider.get()

    private val retryTimeout: Long = DEFAULT_RETRY_TIMEOUT_MS

    private val random: VertxContextPRNG = VertxContextPRNG.current(vertx)

    override fun init(vertx: Vertx, options: JsonObject): SessionStore = this

    override fun retryTimeout(): Long = retryTimeout

    override fun createSession(timeout: Long): Session =
        SharedDataSessionImpl(random, timeout, SessionStore.DEFAULT_SESSIONID_LENGTH)

    override fun createSession(timeout: Long, length: Int): Session =
        SharedDataSessionImpl(random, timeout, length)

    override fun get(cookieValue: String, resultHandler: Handler<AsyncResult<Session>>) {
        val session: Session? =
            lock.withLock {
                log.trace("Retrieving session with ID $cookieValue")
                val session: SharedDataSessionImpl? =
                    redisClient.use { client: Jedis -> client.getSession(sessionId = cookieValue) }
                session?.setPRNG(random)
                session
            }
        resultHandler.handle(Future.succeededFuture(session))
    }

    override fun delete(id: String, resultHandler: Handler<AsyncResult<Void>>) {
        lock.withLock {
            log.trace("Deleting session with ID $id")
            redisClient.use { client: Jedis ->
                client.del(sessionShadowKey(sessionId = id))
                client.del(sessionKey(sessionId = id))
            }
        }
        resultHandler.handle(Future.succeededFuture())
    }

    override fun put(session: Session, resultHandler: Handler<AsyncResult<Void>>) {
        val future: Future<Void> =
            lock.withLock {
                log.trace("Putting session with ID ${session.id()}")
                redisClient.use { client: Jedis ->
                    val oldSession: SharedDataSessionImpl? = client.getSession(sessionId = session.id())
                    val newSession: SharedDataSessionImpl = session as SharedDataSessionImpl
                    if (oldSession != null && oldSession.version() != newSession.version()) {
                        Future.failedFuture("Version mismatch")
                    } else {
                        newSession.incrementVersion()
                        client.psetex(sessionShadowKey(sessionId = session.id()), session.timeout(), "")
                        client.set(
                            sessionKey(sessionId = session.id()).toByteArray(Charsets.UTF_8),
                            sessionCodec.serialize(session = session),
                        )
                        Future.succeededFuture()
                    }
                }
            }
        resultHandler.handle(future)
    }

    override fun clear(resultHandler: Handler<AsyncResult<Void>>) {
        lock.withLock {
            log.trace("Clearing all sessions")
            redisClient.use { client: Jedis ->
                val shadowKeys: Set<String> = client.keys(sessionShadowKey(sessionId = "*"))
                if (shadowKeys.isNotEmpty()) client.del(*shadowKeys.toTypedArray())
                val sessionKeys: Set<String> = client.keys(sessionKey(sessionId = "*"))
                if (sessionKeys.isNotEmpty()) client.del(*sessionKeys.toTypedArray())
            }
        }
        resultHandler.handle(Future.succeededFuture())
    }

    override fun size(resultHandler: Handler<AsyncResult<Int>>) =
        resultHandler.handle(lock.withLock { redisClient.use { Future.succeededFuture(it.keys(sessionShadowKey(sessionId = "*")).size) } })

    override fun close() {
        // No closing operations required.
    }

    private fun Jedis.getSession(sessionId: String): SharedDataSessionImpl? =
        if (this.exists(sessionShadowKey(sessionId = sessionId))) {
            this.get(sessionKey(sessionId = sessionId).toByteArray(Charsets.UTF_8))
                ?.let { sessionCodec.deserialize(bytes = it) }
        } else null

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RedisSessionStore::class.java)

        private const val DEFAULT_RETRY_TIMEOUT_MS: Long = 5 * 1000

        private const val sessionKeyPrefix: String = "uk.co.rafearnold.captainsonar.session."
        internal fun sessionKey(sessionId: String): String = "$sessionKeyPrefix$sessionId"

        private const val sessionShadowKeyPrefix: String = "uk.co.rafearnold.captainsonar.session-shadow."
        internal fun sessionShadowKey(sessionId: String): String = "$sessionShadowKeyPrefix$sessionId"

        internal fun getSessionIdFromShadowKey(shadowKey: String): String =
            shadowKey.substring(sessionShadowKeyPrefix.length)
    }
}
