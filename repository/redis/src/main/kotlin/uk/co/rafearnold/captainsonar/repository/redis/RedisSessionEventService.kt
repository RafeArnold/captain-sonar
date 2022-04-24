package uk.co.rafearnold.captainsonar.repository.redis

import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPubSub
import uk.co.rafearnold.captainsonar.repository.session.SessionCodec
import uk.co.rafearnold.captainsonar.repository.session.SessionEvent
import uk.co.rafearnold.captainsonar.repository.session.SessionEventHandler
import uk.co.rafearnold.captainsonar.repository.session.SessionEventService
import uk.co.rafearnold.captainsonar.repository.session.SessionExpiredEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import kotlin.concurrent.withLock

/**
 * Responsibility of session storage is shared between this class and [RedisSessionStore].
 */
internal class RedisSessionEventService @Inject constructor(
    private val redisClientProvider: RedisClientProvider,
    private val sessionCodec: SessionCodec,
) : SessionEventService {

    private val eventSubscriptions: MutableMap<String, SessionEventHandler> = ConcurrentHashMap()

    private val lock: Lock = ReentrantLock()

    private var redisClient: Jedis? = null

    init {
        redisClientProvider.subscribeToClientConfigChangeEvents {
            lock.withLock {
                redisClient?.let { oldClient: Jedis ->
                    log.debug("Updating redis subscription")
                    redisClient = openRedisSubscription()
                    log.debug("Closing old redis subscription from client $oldClient")
                    oldClient.close()
                }
            }
        }
    }

    private val redisSubscriptionExecutor: Executor =
        // Thread pool of size 2 to allow a new subscription to be opened before the existing one is closed.
        ThreadPoolExecutor(0, 2, 5L, TimeUnit.SECONDS, SynchronousQueue())
    private val internalSubscriptionExecutor: Executor = Executors.newCachedThreadPool()

    override fun subscribeToSessionEvents(handler: SessionEventHandler): String =
        lock.withLock {
            val subscriptionId: String = UUID.randomUUID().toString()
            eventSubscriptions[subscriptionId] = handler
            if (redisClient == null) redisClient = openRedisSubscription()
            subscriptionId
        }

    override fun unsubscribeFromSessionEvents(subscriptionId: String) =
        lock.withLock {
            eventSubscriptions.remove(subscriptionId)
            if (eventSubscriptions.isEmpty()) {
                redisClient?.close()
                redisClient = null
            }
        }

    private fun openRedisSubscription(): Jedis {
        val redisClient = redisClientProvider.get()
        val pubSub: JedisPubSub =
            object : JedisPubSub() {
                override fun onPMessage(pattern: String, channel: String, message: String) {
                    when (message) {
                        "expired" -> {
                            val shadowKey: String = channel.substring(keySpaceChannelPrefix.length)
                            val sessionId: String = RedisSessionStore.getSessionIdFromShadowKey(shadowKey = shadowKey)
                            val sessionKey: String = RedisSessionStore.sessionKey(sessionId = sessionId)
                            val session: SharedDataSessionImpl? =
                                redisClientProvider.get().use { client: Jedis ->
                                    client.getDel(sessionKey.toByteArray(Charsets.UTF_8))
                                        ?.let { sessionCodec.deserialize(bytes = it) }
                                }
                            if (session == null) log.error("Session with key '$sessionKey' is missing")
                            else {
                                val event: SessionEvent = SessionExpiredEvent(session = session)
                                log.debug("Expired session event received: $event")
                                runSubscriptionHandlers(event = event)
                            }
                        }
                    }
                }
            }
        redisSubscriptionExecutor.execute {
            runCatching {
                log.debug("Opening subscription to expired session channel with client $redisClient")
                val pattern: String = keySpaceChannelPrefix + RedisSessionStore.sessionShadowKey(sessionId = "*")
                redisClient.psubscribe(pubSub, pattern)
            }.onFailure {
                log.error("Error occurred while subscribing to expired session channel with client $redisClient", it)
            }
        }
        return redisClient
    }

    private fun runSubscriptionHandlers(event: SessionEvent) {
        for ((subscriptionId: String, handler: SessionEventHandler) in eventSubscriptions) {
            internalSubscriptionExecutor.execute {
                runCatching { handler.handle(event) }
                    .onFailure {
                        log.error("Subscription '$subscriptionId' failed to handle event '$event'", it)
                    }
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RedisSessionEventService::class.java)

        private const val keySpaceChannelPrefix: String = "__keyspace@*__:"
    }
}
