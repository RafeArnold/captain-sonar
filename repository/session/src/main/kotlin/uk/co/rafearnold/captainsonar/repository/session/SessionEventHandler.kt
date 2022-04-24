package uk.co.rafearnold.captainsonar.repository.session

fun interface SessionEventHandler {
    fun handle(event: SessionEvent)
}
