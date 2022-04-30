package uk.co.rafearnold.captainsonar.repository.shareddata

import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.common.Subscription
import uk.co.rafearnold.captainsonar.repository.session.SessionEvent
import uk.co.rafearnold.captainsonar.repository.session.SessionEventService
import uk.co.rafearnold.captainsonar.repository.session.SessionExpiredEvent
import uk.co.rafearnold.captainsonar.shareddata.EntryExpiredEvent
import uk.co.rafearnold.captainsonar.shareddata.SharedMap
import uk.co.rafearnold.captainsonar.shareddata.SharedMapEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Flow
import java.util.concurrent.SubmissionPublisher
import java.util.function.Consumer
import javax.inject.Inject

internal class SharedDataSessionEventService @Inject constructor(
    @SharedDataSessionStoreData dataMap: SharedMap<String, SharedDataSessionImpl>,
) : SessionEventService {

    private val subscriptionPublisher: SubmissionPublisher<SessionEvent> =
        SubmissionPublisher(Executors.newCachedThreadPool(), Flow.defaultBufferSize()) { _, throwable: Throwable ->
            log.error("Subscription failed to handle event", throwable)
        }

    init {
        dataMap.addListener { mapEvent: SharedMapEvent<String, SharedDataSessionImpl> ->
            when (mapEvent) {
                is EntryExpiredEvent -> {
                    val sessionEvent: SessionEvent = SessionExpiredEvent(session = mapEvent.oldValue)
                    log.debug("Expired session event received: $sessionEvent")
                    subscriptionPublisher.submit(sessionEvent)
                }
                else -> log.trace("Ignoring map event '$mapEvent'")
            }
        }
    }

    override fun subscribeToSessionEvents(consumer: Consumer<SessionEvent>): Subscription {
        val subscriptionFuture: CompletableFuture<Void> = subscriptionPublisher.consume(consumer)
        return Subscription { subscriptionFuture.complete(null) }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SharedDataSessionEventService::class.java)
    }
}
