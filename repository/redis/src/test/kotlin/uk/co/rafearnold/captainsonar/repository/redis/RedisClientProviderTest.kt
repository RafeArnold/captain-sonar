package uk.co.rafearnold.captainsonar.repository.redis

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.Jedis
import uk.co.rafearnold.captainsonar.config.ObservableMap
import uk.co.rafearnold.captainsonar.config.ObservableMutableMap
import uk.co.rafearnold.captainsonar.config.ObservableMutableMapImpl
import uk.co.rafearnold.captainsonar.config.addListener
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Testcontainers
class RedisClientProviderTest {

    companion object {
        private const val password1 = "test_password1"
        private const val password2 = "test_password2"

        @Container
        private val redisContainer1: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("redis").withTag("alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server --requirepass $password1")

        @Container
        private val redisContainer2: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("redis").withTag("alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server --requirepass $password2")

        @Container
        private val redisContainer3: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("redis").withTag("alpine"))
                .withExposedPorts(6379)
    }

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
        createClient(host = redisContainer1.host, port = redisContainer1.firstMappedPort, password = password1)
            .flushAll()
        createClient(host = redisContainer2.host, port = redisContainer2.firstMappedPort, password = password2)
            .flushAll()
        Jedis(redisContainer3.host, redisContainer3.firstMappedPort).flushAll()
    }

    @Test
    @Suppress("ControlFlowWithEmptyBody")
    fun `when the redis connection config is updated then the client is updated`() {
        val container1Keys: Set<String> = setOf("test_key1")
        val container2Keys: Set<String> = setOf("test_key2")
        val container3Keys: Set<String> = setOf("test_key3")
        populateContainers(
            container1Key = container1Keys,
            container2Key = container2Keys,
            container3Key = container3Keys
        )

        val appConfig: ObservableMutableMap<String, String> = ObservableMutableMapImpl(ConcurrentHashMap())
        val listenerEvents: MutableSet<ObservableMap.ListenEvent<String, String>> = ConcurrentHashMap.newKeySet()
        mockkStatic("uk.co.rafearnold.captainsonar.config.ObservableMapExtensionFunctionsKt")
        every { appConfig.addListener(any<String>(), any()) } answers {
            val originalListener: ObservableMap.Listener<String, String> = thirdArg()
            val wrappingListener: ObservableMap.Listener<String, String> =
                ObservableMap.Listener {
                    try {
                        originalListener.handle(it)
                    } finally {
                        listenerEvents.add(it)
                    }
                }
            val regex = Regex(secondArg())
            appConfig.addListener({ regex.matches(it) }, wrappingListener)
        }

        appConfig["redis.connection.host"] = "localhost"

        appConfig["redis.connection.port"] = redisContainer1.firstMappedPort.toString()
        appConfig["redis.connection.password"] = password1

        val provider = RedisClientProvider(appConfig = appConfig)

        assertEquals(container1Keys, provider.get().keys("*"))

        appConfig["redis.connection.port"] = redisContainer2.firstMappedPort.toString()
        appConfig["redis.connection.password"] = password2

        CompletableFuture.runAsync { while (listenerEvents.size != 2); }.get(10, TimeUnit.SECONDS)

        assertEquals(container2Keys, provider.get().keys("*"))

        appConfig["redis.connection.port"] = redisContainer3.firstMappedPort.toString()
        appConfig.remove("redis.connection.password")

        CompletableFuture.runAsync { while (listenerEvents.size != 4); }.get(2, TimeUnit.SECONDS)

        assertEquals(container3Keys, provider.get().keys("*"))
    }

    private fun populateContainers(container1Key: Set<String>, container2Key: Set<String>, container3Key: Set<String>) {
        val container1Data: List<String> =
            container1Key.associateWith { UUID.randomUUID().toString() }.flatMap { listOf(it.key, it.value) }
        createClient(host = redisContainer1.host, port = redisContainer1.firstMappedPort, password = password1)
            .mset(*container1Data.toTypedArray())
        val container2Data: List<String> =
            container2Key.associateWith { UUID.randomUUID().toString() }.flatMap { listOf(it.key, it.value) }
        createClient(host = redisContainer2.host, port = redisContainer2.firstMappedPort, password = password2)
            .mset(*container2Data.toTypedArray())
        val container3Data: List<String> =
            container3Key.associateWith { UUID.randomUUID().toString() }.flatMap { listOf(it.key, it.value) }
        Jedis(redisContainer3.host, redisContainer3.firstMappedPort)
            .mset(*container3Data.toTypedArray())
    }

    private fun createClient(host: String, port: Int, password: String): Jedis =
        Jedis(host, port, DefaultJedisClientConfig.builder().password(password).build())
}
