package uk.co.rafearnold.captainsonar.config

import io.vertx.config.ConfigChange
import io.vertx.config.ConfigRetriever
import io.vertx.core.json.JsonObject
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.common.toCompletableFuture
import uk.co.rafearnold.commons.config.ObservableMutableMap
import uk.co.rafearnold.commons.config.ObservableMutableMapImpl
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ConfigProvider @Inject constructor(
    initialConfig: JsonObject,
    private val configRetriever: ConfigRetriever
) : Provider<ObservableMutableMap<String, String>>, Register {

    private val config: ObservableMutableMap<String, String> =
        ObservableMutableMapImpl(backingMap = initialConfig.toStringMap())

    override fun get(): ObservableMutableMap<String, String> = config

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            configRetriever.listen { configChange: ConfigChange ->
                config.setTo(configChange.newConfiguration.toStringMap())
            }
        }.thenCompose {
            configRetriever.config.toCompletableFuture()
                .thenAccept { config.setTo(it.toStringMap()) }
        }

    private fun <K, V> MutableMap<K, V>.setTo(other: Map<K, V>) {
        synchronized(this) {
            this.keys.retainAll(other.keys)
            this.putAll(other)
        }
    }

    private fun JsonObject.toStringMap(): MutableMap<String, String> =
        this.map.mapValues { it.value.toString() }.toMutableMap()
}
