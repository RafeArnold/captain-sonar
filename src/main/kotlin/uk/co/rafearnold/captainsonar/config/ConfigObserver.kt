package uk.co.rafearnold.captainsonar.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.common.Register
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

class ConfigObserver @Inject constructor(
    private val config: ObservableMap<String, String>
) : Register {

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync { config.addListener(".*") { log.debug(it.toString()) } }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ConfigObserver::class.java)
    }
}
