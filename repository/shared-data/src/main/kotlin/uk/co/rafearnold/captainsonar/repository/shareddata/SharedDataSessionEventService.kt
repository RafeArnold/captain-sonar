package uk.co.rafearnold.captainsonar.repository.shareddata

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.repository.session.SessionCodec
import uk.co.rafearnold.captainsonar.repository.session.SessionEvent
import uk.co.rafearnold.captainsonar.repository.session.SessionEventHandler
import uk.co.rafearnold.captainsonar.repository.session.SessionEventService
import uk.co.rafearnold.captainsonar.repository.session.SessionExpiredEvent
import uk.co.rafearnold.captainsonar.shareddata.EntryExpiredEvent
import uk.co.rafearnold.captainsonar.shareddata.SharedMap
import uk.co.rafearnold.captainsonar.shareddata.SharedMapEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject

internal class SharedDataSessionEventService @Inject constructor(
    @SharedDataSessionStoreData dataMap: SharedMap<String, ByteArray>,
    private val sessionCodec: SessionCodec,
) : SessionEventService {

    private val expiredEventSubscriptions: MutableMap<String, SessionEventHandler> = ConcurrentHashMap()

    private val subscriptionHandlerExecutor: Executor = Executors.newCachedThreadPool()

    init {
        dataMap.addListener { mapEvent: SharedMapEvent<String, ByteArray> ->
            when (mapEvent) {
                is EntryExpiredEvent -> {
                    val sessionEvent: SessionEvent =
                        SessionExpiredEvent(session = sessionCodec.deserialize(bytes = mapEvent.oldValue))
                    log.debug("Expired session event received: $sessionEvent")
                    runSubscriptionHandlers(event = sessionEvent)
                }
                else -> log.trace("Ignoring map event '$mapEvent'")
            }
        }
    }

    override fun subscribeToSessionEvents(handler: SessionEventHandler): String {
        val subscriptionId: String = UUID.randomUUID().toString()
        expiredEventSubscriptions[subscriptionId] = handler
        return subscriptionId
    }

    override fun unsubscribeFromSessionEvents(subscriptionId: String) {
        expiredEventSubscriptions.remove(subscriptionId)
    }

    private fun runSubscriptionHandlers(event: SessionEvent) {
        for ((subscriptionId: String, handler: SessionEventHandler) in expiredEventSubscriptions) {
            subscriptionHandlerExecutor.execute {
                runCatching { handler.handle(event) }
                    .onFailure {
                        log.error("Subscription '$subscriptionId' failed to handle event '$event'", it)
                    }
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SharedDataSessionEventService::class.java)
    }
}
