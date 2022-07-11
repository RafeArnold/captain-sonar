package uk.co.rafearnold.captainsonar.repository.shareddata

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
import uk.co.rafearnold.commons.shareddata.SharedMap
import java.util.concurrent.TimeUnit

internal class SharedDataSessionStore(
    vertx: Vertx,
    private val sessionMap: SharedMap<String, SharedDataSessionImpl>,
) : SessionStore {

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
        val session: SharedDataSessionImpl? = sessionMap[cookieValue]
        session?.setPRNG(random)
        resultHandler.handle(Future.succeededFuture(session))
    }

    override fun delete(id: String, resultHandler: Handler<AsyncResult<Void>>) {
        log.trace("Deleting session with ID $id")
        sessionMap.remove(id)
        resultHandler.handle(Future.succeededFuture())
    }

    override fun put(session: Session, resultHandler: Handler<AsyncResult<Void>>) {
        log.trace("Putting session with ID ${session.id()}")
        val oldSession: SharedDataSessionImpl? = sessionMap[session.id()]
        val newSession: SharedDataSessionImpl = session as SharedDataSessionImpl
        if (oldSession != null && oldSession.version() != newSession.version()) {
            resultHandler.handle(Future.failedFuture("Version mismatch"))
            return
        }
        newSession.incrementVersion()
        sessionMap.put(
            key = session.id(),
            value = session,
            ttl = session.timeout(),
            ttlUnit = TimeUnit.MILLISECONDS
        )
        resultHandler.handle(Future.succeededFuture())
    }

    override fun clear(resultHandler: Handler<AsyncResult<Void>>) {
        log.trace("Clearing all sessions")
        sessionMap.clear()
        resultHandler.handle(Future.succeededFuture())
    }

    override fun size(resultHandler: Handler<AsyncResult<Int>>) {
        resultHandler.handle(Future.succeededFuture(sessionMap.size))
    }

    override fun close() {
        // No closing operations required.
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SharedDataSessionStore::class.java)

        private const val DEFAULT_RETRY_TIMEOUT_MS: Long = 5 * 1000
    }
}
