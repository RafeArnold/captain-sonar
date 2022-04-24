package uk.co.rafearnold.captainsonar.repository.session

import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl

sealed interface SessionEvent

data class SessionExpiredEvent(val session: SharedDataSessionImpl) : SessionEvent
