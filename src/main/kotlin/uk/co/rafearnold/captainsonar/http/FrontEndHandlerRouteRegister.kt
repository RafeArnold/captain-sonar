package uk.co.rafearnold.captainsonar.http

import io.vertx.ext.web.Route
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.commons.config.ObservableMap
import uk.co.rafearnold.commons.config.addListener
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class FrontEndHandlerRouteRegister @Inject constructor(
    private val router: Router,
    private val appConfig: ObservableMap<String, String>
) : Register {

    private var route: Route? = null

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            appConfig.addListener(keyRegex = "\\Q$frontEndDirPropName\\E") {
                val dir: String? = it.newValue
                if (dir == null) log.error("Null front-end directory provided")
                else {
                    log.debug("Updating front-end directory to '$dir'")
                    setRoute(dir = dir)
                    log.debug("Front-end directory updated to '$dir'")
                }
            }
            setRoute(dir = appConfig.getValue(frontEndDirPropName))
        }

    private fun setRoute(dir: String) {
        route?.remove()
        route = router.get("/play/*").handler(StaticHandler.create(dir))
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(FrontEndHandlerRouteRegister::class.java)
        private const val frontEndDirPropName = "front-end.dir"
    }
}
