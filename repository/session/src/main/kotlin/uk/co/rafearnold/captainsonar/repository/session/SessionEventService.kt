package uk.co.rafearnold.captainsonar.repository.session

interface SessionEventService {

    fun subscribeToSessionEvents(handler: SessionEventHandler): String

    fun unsubscribeFromSessionEvents(subscriptionId: String)
}
