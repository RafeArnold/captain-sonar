package uk.co.rafearnold.captainsonar.repository.redis

import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import redis.clients.jedis.Jedis
import uk.co.rafearnold.commons.config.ObservableMap
import uk.co.rafearnold.commons.config.ObservableMutableMapImpl
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Testcontainers
class RedisGameIdRepositoryTest {

    companion object {
        @Container
        private val redisContainer: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("redis").withTag("alpine"))
                .withExposedPorts(6379)
                .withCommand("redis-server --timeout 0")
    }

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
        Jedis(redisContainer.host, redisContainer.firstMappedPort).flushAll()
    }

    @Test
    fun `when the index is not in the db then it is instantiated as 0 when retrieved`() {
        val appConfig: ObservableMap<String, String> =
            ObservableMutableMapImpl(
                backingMap = mutableMapOf(
                    "redis.connection.host" to redisContainer.host,
                    "redis.connection.port" to redisContainer.firstMappedPort.toString()
                )
            )
        val redisClientProvider = RedisClientProvider(appConfig = appConfig)
        val gameIdRepository = RedisGameIdRepository(redisClientProvider = redisClientProvider)

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)

        assertEquals(setOf<String>(), redisClient.keys("*"))

        assertEquals(0, gameIdRepository.getAndIncrementIdIndex())

        assertEquals(setOf("uk.co.rafearnold.captainsonar.game.id-generator.index"), redisClient.keys("*"))
        assertEquals("1", redisClient.get("uk.co.rafearnold.captainsonar.game.id-generator.index"))
    }

    @Test
    fun `index can be retrieved and incremented concurrently`() {
        val totalIncrements = 1000
        val appConfig: ObservableMap<String, String> =
            ObservableMutableMapImpl(
                backingMap = mutableMapOf(
                    "redis.connection.host" to redisContainer.host,
                    "redis.connection.port" to redisContainer.firstMappedPort.toString(),
                    "redis.connection.pool-size" to (totalIncrements + 1).toString(),
                )
            )
        val redisClientProvider = RedisClientProvider(appConfig = appConfig)
        val gameIdRepository = RedisGameIdRepository(redisClientProvider = redisClientProvider)

        val initialIndex = 53434
        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        redisClient.set("uk.co.rafearnold.captainsonar.game.id-generator.index", initialIndex.toString())

        assertEquals(initialIndex, gameIdRepository.getAndIncrementIdIndex())

        val futures: Array<CompletableFuture<Int>> =
            Array(totalIncrements) { CompletableFuture.supplyAsync { gameIdRepository.getAndIncrementIdIndex() } }

        CompletableFuture.allOf(*futures).get(10, TimeUnit.SECONDS)

        val indices: Set<Int> = futures.map { it.get(1, TimeUnit.SECONDS) }.toSet()
        assertEquals(totalIncrements, indices.size)
        assertEquals(initialIndex + 1, indices.minOrNull())
        assertEquals(initialIndex + totalIncrements, indices.maxOrNull())

        assertEquals(setOf("uk.co.rafearnold.captainsonar.game.id-generator.index"), redisClient.keys("*"))
        assertEquals(
            (initialIndex + totalIncrements + 1).toString(),
            redisClient.get("uk.co.rafearnold.captainsonar.game.id-generator.index")
        )
    }

    @Test
    fun `when the index in the db reaches the integer maximum then the returned index is reset to 0`() {
        val appConfig: ObservableMap<String, String> =
            ObservableMutableMapImpl(
                backingMap = mutableMapOf(
                    "redis.connection.host" to redisContainer.host,
                    "redis.connection.port" to redisContainer.firstMappedPort.toString(),
                )
            )
        val redisClientProvider = RedisClientProvider(appConfig = appConfig)
        val gameIdRepository = RedisGameIdRepository(redisClientProvider = redisClientProvider)

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        redisClient.set("uk.co.rafearnold.captainsonar.game.id-generator.index", "${Int.MAX_VALUE - 2}")

        assertEquals(Int.MAX_VALUE - 2, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(Int.MAX_VALUE - 1, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(0, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(1, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(2, gameIdRepository.getAndIncrementIdIndex())

        assertEquals(
            (Int.MAX_VALUE.toLong() + 3).toString(),
            redisClient.get("uk.co.rafearnold.captainsonar.game.id-generator.index")
        )
    }

    @Test
    fun `when the index in the db reaches double the integer maximum then the returned index is reset to 0`() {
        val appConfig: ObservableMap<String, String> =
            ObservableMutableMapImpl(
                backingMap = mutableMapOf(
                    "redis.connection.host" to redisContainer.host,
                    "redis.connection.port" to redisContainer.firstMappedPort.toString(),
                )
            )
        val redisClientProvider = RedisClientProvider(appConfig = appConfig)
        val gameIdRepository = RedisGameIdRepository(redisClientProvider = redisClientProvider)

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        redisClient.set("uk.co.rafearnold.captainsonar.game.id-generator.index", "${2 * Int.MAX_VALUE.toLong() - 2}")

        assertEquals(Int.MAX_VALUE - 2, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(Int.MAX_VALUE - 1, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(0, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(1, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(2, gameIdRepository.getAndIncrementIdIndex())

        assertEquals(
            (2 * Int.MAX_VALUE.toLong() + 3).toString(),
            redisClient.get("uk.co.rafearnold.captainsonar.game.id-generator.index")
        )
    }

    @Test
    fun `when the index in the db is negative then the returned index cycles up to the integer maximum`() {
        val appConfig: ObservableMap<String, String> =
            ObservableMutableMapImpl(
                backingMap = mutableMapOf(
                    "redis.connection.host" to redisContainer.host,
                    "redis.connection.port" to redisContainer.firstMappedPort.toString(),
                )
            )
        val redisClientProvider = RedisClientProvider(appConfig = appConfig)
        val gameIdRepository = RedisGameIdRepository(redisClientProvider = redisClientProvider)

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        redisClient.set("uk.co.rafearnold.captainsonar.game.id-generator.index", "-2")

        assertEquals(Int.MAX_VALUE - 2, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(Int.MAX_VALUE - 1, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(0, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(1, gameIdRepository.getAndIncrementIdIndex())
        assertEquals(2, gameIdRepository.getAndIncrementIdIndex())

        assertEquals("3", redisClient.get("uk.co.rafearnold.captainsonar.game.id-generator.index"))
    }
}
