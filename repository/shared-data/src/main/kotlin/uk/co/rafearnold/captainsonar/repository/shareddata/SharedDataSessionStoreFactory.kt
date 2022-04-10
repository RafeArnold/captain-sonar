package uk.co.rafearnold.captainsonar.repository.shareddata

import io.vertx.core.Vertx
import io.vertx.ext.web.sstore.SessionStore
import uk.co.rafearnold.captainsonar.repository.session.SessionStoreFactory
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import javax.inject.Inject

internal class SharedDataSessionStoreFactory @Inject constructor(
    private val vertx: Vertx,
    private val sharedDataService: SharedDataService,
) : SessionStoreFactory {

    override fun create(): SessionStore =
        SharedDataSessionStore(vertx = vertx, sharedDataService = sharedDataService)
}
