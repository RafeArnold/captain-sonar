package uk.co.rafearnold.captainsonar.config

import io.vertx.config.ConfigChange
import io.vertx.config.ConfigRetriever
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import uk.co.rafearnold.captainsonar.common.toCompletableFuture
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class ConfigRetrieverProviderTest {

    @Test
    fun `config retriever can be provided with no properties file configured`() {
        val vertx: Vertx = Vertx.vertx()

        val provider = ConfigRetrieverProvider(vertx = vertx)

        System.setProperty("test_systemProperty1Key", "test_systemProperty1Value")

        val configRetriever: ConfigRetriever = provider.get()
        val config: JsonObject = configRetriever.config.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals("test_systemProperty1Value", config.getString("test_systemProperty1Key"))

        vertx.close()
    }

    @Test
    fun `a properties file can be configured via the system properties`() {
        val vertx: Vertx = Vertx.vertx()

        val provider = ConfigRetrieverProvider(vertx = vertx)

        System.setProperty("test_systemProperty1Key", "test_systemProperty1Value")
        System.setProperty("application.properties.path", "test-application.properties")

        val configRetriever: ConfigRetriever = provider.get()
        val config: JsonObject = configRetriever.config.toCompletableFuture().get(2, TimeUnit.SECONDS)

        assertEquals("test_systemProperty1Value", config.getString("test_systemProperty1Key"))
        assertEquals("test_applicationProperty1Value", config.getString("test_applicationProperty1Key"))
        assertEquals("test_applicationProperty2Value", config.getString("test_applicationProperty2Key"))
        assertEquals("245224", config.getString("test_applicationProperty3Key"))
        assertEquals("false", config.getString("test_applicationProperty4Key"))

        vertx.close()
    }

    @Test
    @Suppress("ControlFlowWithEmptyBody")
    fun `the config scan period can be configured via the system properties`() {
        val vertx: Vertx = Vertx.vertx()

        val provider = ConfigRetrieverProvider(vertx = vertx)

        val scanPeriodMs: Long = 10
        System.setProperty("application.properties.scanPeriodMs", scanPeriodMs.toString())

        val configRetriever: ConfigRetriever = provider.get()
        val configChanges: Queue<ConfigChange> = ConcurrentLinkedQueue()
        configRetriever.listen { configChanges.add(it) }

        System.setProperty("test_systemProperty1Key", "test_systemProperty1Value1")

        assertEquals(0, configChanges.size)
        CompletableFuture.runAsync { while (configChanges.size != 1); }.get(500, TimeUnit.MILLISECONDS)
        val configChange1: ConfigChange = configChanges.poll()
        val oldConfig1: JsonObject = configChange1.previousConfiguration
        assertNull(oldConfig1.getString("test_systemProperty1Key"))
        val newConfig1: JsonObject = configChange1.newConfiguration
        assertEquals("test_systemProperty1Value1", newConfig1.getString("test_systemProperty1Key"))

        System.setProperty("test_systemProperty1Key", "test_systemProperty1Value2")

        assertEquals(0, configChanges.size)
        CompletableFuture.runAsync { while (configChanges.size != 1); }.get(500, TimeUnit.MILLISECONDS)
        val configChange2: ConfigChange = configChanges.poll()
        val oldConfig2: JsonObject = configChange2.previousConfiguration
        assertEquals("test_systemProperty1Value1", oldConfig2.getString("test_systemProperty1Key"))
        val newConfig2: JsonObject = configChange2.newConfiguration
        assertEquals("test_systemProperty1Value2", newConfig2.getString("test_systemProperty1Key"))

        vertx.close()
    }
}
