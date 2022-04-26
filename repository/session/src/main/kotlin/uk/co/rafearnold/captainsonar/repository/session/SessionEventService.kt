package uk.co.rafearnold.captainsonar.repository.session

import uk.co.rafearnold.captainsonar.common.Subscription
import java.util.function.Consumer

interface SessionEventService {

    fun subscribeToSessionEvents(consumer: Consumer<SessionEvent>): Subscription
}
