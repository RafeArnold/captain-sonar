package uk.co.rafearnold.captainsonar.repository.session

import io.vertx.ext.web.sstore.SessionStore

interface SessionStoreFactory {

    fun create(): SessionStore
}
