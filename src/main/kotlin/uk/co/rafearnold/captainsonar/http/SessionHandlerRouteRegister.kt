package uk.co.rafearnold.captainsonar.http

import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.SessionHandler
import io.vertx.ext.web.sstore.SessionStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.config.ObservableMap
import uk.co.rafearnold.captainsonar.config.addListener
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class SessionHandlerRouteRegister @Inject constructor(
    private val sessionStore: SessionStore,
    private val router: Router,
    private val appConfig: ObservableMap<String, String>,
) : Register {

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            val handler: SessionHandler =
                SessionHandler.create(sessionStore)
                    .setSessionCookieName("uk.co.rafearnold.captainsonar.session")
                    .setCookieHttpOnlyFlag(true)
                    .setCookieSecureFlag(true)
                    .setSessionTimeout(appConfig[sessionTimeoutPropName]?.toLong() ?: DEFAULT_SESSION_TIMEOUT_MS)
            router.route().handler(handler)
            appConfig.addListener(keyRegex = "\\Q$sessionTimeoutPropName\\E") {
                val sessionTimeoutMsString: String? = it.newValue
                val sessionTimeoutMs: Long =
                    if (sessionTimeoutMsString == null) {
                        log.error("Custom session timeout removed")
                        DEFAULT_SESSION_TIMEOUT_MS
                    } else {
                        log.debug("Updating session timeout to '$sessionTimeoutMsString'")
                        sessionTimeoutMsString.toLongOrNull()
                            .let { timeoutMs ->
                                if (timeoutMs == null) {
                                    log.error("Configured session timeout '$sessionTimeoutMsString' is not a valid integer")
                                    DEFAULT_SESSION_TIMEOUT_MS
                                } else timeoutMs
                            }
                    }
                handler.setSessionTimeout(sessionTimeoutMs)
                log.debug("Session timeout updated to '$sessionTimeoutMs'")
            }
        }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SessionHandlerRouteRegister::class.java)

        private const val sessionTimeoutPropName = "session.timeout.ms"

        /**
         * Default time, in ms, that a session lasts for without being accessed before expiring.
         */
        private const val DEFAULT_SESSION_TIMEOUT_MS: Long = 30 * 60 * 1000 // 30 minutes.
    }
}
