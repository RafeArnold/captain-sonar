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
        val propertiesFilepath: String = System.getProperty("application.properties.path")
        val propertiesFileStoreOptions: ConfigStoreOptions =
            ConfigStoreOptions()
                .setType("file")
                .setFormat("properties")
                .setConfig(JsonObject().put("path", propertiesFilepath).put("raw-data", true))

        val systemPropertiesStoreOptions: ConfigStoreOptions = ConfigStoreOptions().setType("sys")
        val envVariablesStoreOptions: ConfigStoreOptions = ConfigStoreOptions().setType("env")

        val scanPeriod: Long? = System.getProperty("application.properties.scanPeriodMs")?.toLong()

        val retrieverOptions: ConfigRetrieverOptions =
            ConfigRetrieverOptions()
                .addStore(propertiesFileStoreOptions)
                .addStore(systemPropertiesStoreOptions)
                .addStore(envVariablesStoreOptions)
                .apply { if (scanPeriod != null) setScanPeriod(scanPeriod) }

        return ConfigRetriever.create(vertx, retrieverOptions)
    }
}
