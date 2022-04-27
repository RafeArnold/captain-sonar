package uk.co.rafearnold.captainsonar.config

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import javax.inject.Inject
import javax.inject.Provider

class ConfigRetrieverProvider @Inject constructor(
    private val vertx: Vertx
) : Provider<ConfigRetriever> {

    override fun get(): ConfigRetriever {
        val retrieverOptions = ConfigRetrieverOptions()

        val propertiesFilepath: String? =
            System.getProperty("application.properties.path")
                ?: System.getenv("APPLICATION_PROPERTIES_PATH")
        if (propertiesFilepath != null) {
            val propertiesFileStoreOptions: ConfigStoreOptions =
                ConfigStoreOptions()
                    .setType("file")
                    .setFormat("properties")
                    .setConfig(JsonObject().put("path", propertiesFilepath).put(rawDataConfigName, true))
            retrieverOptions.addStore(propertiesFileStoreOptions)
        }

        val systemPropertiesStoreOptions: ConfigStoreOptions =
            ConfigStoreOptions()
                .setType("sys")
                .setConfig(JsonObject().put("cache", false).put(rawDataConfigName, true))
        retrieverOptions.addStore(systemPropertiesStoreOptions)
        val environmentPropertiesStoreOptions: ConfigStoreOptions =
            ConfigStoreOptions()
                .setType("env")
                .setConfig(JsonObject().put(rawDataConfigName, true))
        retrieverOptions.addStore(environmentPropertiesStoreOptions)
        val jsonConfigStoreOptions: ConfigStoreOptions =
            ConfigStoreOptions()
                .setType("json")
                .setConfig(vertx.orCreateContext.config())
        retrieverOptions.addStore(jsonConfigStoreOptions)

        val scanPeriod: Long? =
            (System.getProperty("application.properties.scanPeriodMs")
                ?: System.getenv("APPLICATION_PROPERTIES_SCAN_PERIOD_MS"))
                ?.toLong()
        if (scanPeriod != null) retrieverOptions.scanPeriod = scanPeriod

        return ConfigRetriever.create(vertx, retrieverOptions)
    }

    companion object {
        private const val rawDataConfigName = "raw-data"
    }
}
