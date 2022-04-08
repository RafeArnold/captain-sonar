package uk.co.rafearnold.captainsonar.http

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServer
import io.vertx.ext.web.Router
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.common.toCompletableFuture
import uk.co.rafearnold.captainsonar.config.ObservableMap
import uk.co.rafearnold.captainsonar.config.addListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore
import javax.inject.Inject

class ServerRegister @Inject constructor(
    private val vertx: Vertx,
    private val router: Router,
    private val appConfig: ObservableMap<String, String>
) : Register {

    private var server: HttpServer? = null

    private val semaphore: Semaphore = Semaphore(1)

    override fun register(): CompletableFuture<Void> =
        CompletableFuture
            .runAsync {
                appConfig.addListener(keyRegex = "\\Q$serverPortPropName\\E") {
                    val serverPort: String? = it.newValue
                    if (serverPort == null) log.error("Null server port provided")
                    else {
                        val serverPortInt: Int? = serverPort.toIntOrNull()
                        if (serverPortInt == null) log.error("Invalid server port provided: '$serverPort'")
                        else restartServer(serverPortInt)
                    }
                }
            }
            .thenCompose { restartServer(appConfig.getValue(serverPortPropName).toInt()) }

    private fun restartServer(serverPort: Int): CompletableFuture<Void> =
        CompletableFuture
            .runAsync { semaphore.acquire() }
            .thenCompose {
                if (serverPort != server?.actualPort()) {
                    log.info("Starting server on port $serverPort")
                    vertx.createHttpServer()
                        .requestHandler(router)
                        .listen(serverPort)
                        .toCompletableFuture()
                        .thenCompose<Void> {
                            (this.server?.close()?.toCompletableFuture()
                                ?.thenRun { log.info("Server on port ${this.server?.actualPort()} closed") }
                                ?: CompletableFuture.completedFuture(null))
                                .thenRun {
                                    log.info("Server started on port $serverPort")
                                    this.server = it
                                }
                        }
                } else CompletableFuture.completedFuture(null)
            }
            .whenComplete { _, e: Throwable? -> if (e != null) log.error("Failed to start server", e) }
            .whenComplete { _, _ -> semaphore.release() }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ServerRegister::class.java)

        private const val serverPortPropName = "server.port"
    }
}
