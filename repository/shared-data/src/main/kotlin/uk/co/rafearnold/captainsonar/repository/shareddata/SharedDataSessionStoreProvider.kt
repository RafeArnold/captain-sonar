package uk.co.rafearnold.captainsonar.repository.shareddata

import io.vertx.core.Vertx
import io.vertx.ext.web.sstore.SessionStore
import uk.co.rafearnold.captainsonar.repository.session.SessionCodec
import uk.co.rafearnold.captainsonar.shareddata.SharedMap
import javax.inject.Inject
import javax.inject.Provider

internal class SharedDataSessionStoreProvider @Inject constructor(
    vertx: Vertx,
    @SharedDataSessionStoreData sessionMap: SharedMap<String, ByteArray>,
    sessionCodec: SessionCodec,
) : Provider<SessionStore> {

    private val sessionStore: SessionStore =
        SharedDataSessionStore(vertx = vertx, sessionMap = sessionMap, sessionCodec = sessionCodec)

    override fun get(): SessionStore = sessionStore
}
