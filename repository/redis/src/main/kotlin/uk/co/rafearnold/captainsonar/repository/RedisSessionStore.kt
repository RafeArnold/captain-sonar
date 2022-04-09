package uk.co.rafearnold.captainsonar.repository

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.VertxContextPRNG
import io.vertx.ext.web.Session
import io.vertx.ext.web.sstore.SessionStore
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.params.SetParams

internal class RedisSessionStore(
    vertx: Vertx,
    private val redisClientProvider: RedisClientProvider,
) : SessionStore {

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
        log.trace("Retrieving session with ID $cookieValue")
        val session: SharedDataSessionImpl? = getSession(sessionId = cookieValue)
        session?.setPRNG(random)
        resultHandler.handle(Future.succeededFuture(session))
    }

    override fun delete(id: String, resultHandler: Handler<AsyncResult<Void>>) {
        log.trace("Deleting session with ID $id")
        redisClient.del(sessionKey(sessionId = id))
        resultHandler.handle(Future.succeededFuture())
    }

    override fun put(session: Session, resultHandler: Handler<AsyncResult<Void>>) {
        log.trace("Putting session with ID ${session.id()}")
        val oldSession: SharedDataSessionImpl? = getSession(sessionId = session.id())
        val newSession: SharedDataSessionImpl = session as SharedDataSessionImpl
        if (oldSession != null && oldSession.version() != newSession.version()) {
            resultHandler.handle(Future.failedFuture("Version mismatch"))
            return
        }
        newSession.incrementVersion()
        redisClient.set(sessionKey(sessionId = session.id()), session.serialize(), SetParams().px(session.timeout()))
        resultHandler.handle(Future.succeededFuture())
    }

    override fun clear(resultHandler: Handler<AsyncResult<Void>>) {
        log.trace("Clearing all sessions")
        val keys: Set<ByteArray> = getAllSessionKeys()
        redisClient.del(*keys.toTypedArray())
        resultHandler.handle(Future.succeededFuture())
    }

    override fun size(resultHandler: Handler<AsyncResult<Int>>) {
        resultHandler.handle(Future.succeededFuture(getAllSessionKeys().size))
    }

    override fun close() {
        // No closing operations required.
    }

    private fun getSession(sessionId: String): SharedDataSessionImpl? =
        redisClient.get(sessionKey(sessionId = sessionId))?.deserializeToSession()

    private fun getAllSessionKeys(): Set<ByteArray> = redisClient.keys(sessionKey("*"))

    private fun ByteArray.deserializeToSession(): SharedDataSessionImpl {
        val session = SharedDataSessionImpl()
        session.readFromBuffer(0, Buffer.buffer(this))
        return session
    }

    private fun SharedDataSessionImpl.serialize(): ByteArray {
        val buffer: Buffer = Buffer.buffer()
        this.writeToBuffer(buffer)
        return buffer.bytes
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RedisSessionStore::class.java)

        private const val DEFAULT_RETRY_TIMEOUT_MS: Long = 5 * 1000
        private const val sessionKeyPrefix: String = "uk.co.rafearnold.captainsonar.session."
        private fun sessionKey(sessionId: String): ByteArray =
            "$sessionKeyPrefix$sessionId".toByteArray(Charsets.UTF_8)
    }
}
