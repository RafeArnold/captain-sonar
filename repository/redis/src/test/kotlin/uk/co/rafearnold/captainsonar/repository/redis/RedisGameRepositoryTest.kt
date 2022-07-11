package uk.co.rafearnold.captainsonar.repository.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import redis.clients.jedis.Jedis
import uk.co.rafearnold.captainsonar.common.GameAlreadyExistsException
import uk.co.rafearnold.captainsonar.common.NoSuchGameFoundException
import uk.co.rafearnold.captainsonar.repository.AddPlayerOperation
import uk.co.rafearnold.captainsonar.repository.SetStartedOperation
import uk.co.rafearnold.captainsonar.repository.StoredGame
import uk.co.rafearnold.captainsonar.repository.StoredPlayer
import uk.co.rafearnold.captainsonar.repository.UpdateStoredGameOperation
import uk.co.rafearnold.commons.shareddata.SharedDataService
import uk.co.rafearnold.commons.shareddata.simple.SimpleClusterManager
import java.util.concurrent.TimeUnit

@Testcontainers
class RedisGameRepositoryTest {

    companion object {
        @Container
        private val redisContainer: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("redis").withTag("alpine"))
                .withExposedPorts(6379)
    }

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
        SimpleClusterManager.clearAllClusters()
        Jedis(redisContainer.host, redisContainer.firstMappedPort).flushAll()
    }

    @Test
    fun `a game can be created`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
                objectMapper = objectMapper
            )

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        every { redisClientProvider.get() } returns redisClient

        val gameId = "test_gameId"
        val storedGame =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                    "test_playerId3" to StoredPlayer(name = "test_playerName3"),
                ),
                started = false
            )
        val ttlMs: Long = 100
        repository.createGame(gameId = gameId, game = storedGame, ttl = ttlMs, ttlUnit = TimeUnit.MILLISECONDS)

        val actualResult: String = redisClient.get("uk.co.rafearnold.captainsonar.game.$gameId")
        val expectedResult =
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"},"test_playerId2":{"name":"test_playerName2"},"test_playerId3":{"name":"test_playerName3"}},"started":false}"""
        assertEquals(expectedResult, actualResult)

        // Verify the game is deleted after the specified TTL.
        Thread.sleep(ttlMs)
        assertNull(redisClient.get("uk.co.rafearnold.captainsonar.game.$gameId"))
    }

    @Test
    fun `when a game is created with the same id as an existing game then an exception is thrown and no operation is performed`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
                objectMapper = objectMapper
            )

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        every { redisClientProvider.get() } returns redisClient

        val gameId = "test_gameId"

        val originalValue = "test_originalValue"
        redisClient.set("uk.co.rafearnold.captainsonar.game.$gameId", originalValue)

        val storedGame =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                    "test_playerId3" to StoredPlayer(name = "test_playerName3"),
                ),
                started = false
            )
        val ttlMs: Long = 100
        val exception: GameAlreadyExistsException =
            assertThrows {
                repository.createGame(gameId = gameId, game = storedGame, ttl = ttlMs, ttlUnit = TimeUnit.MILLISECONDS)
            }
        assertEquals(gameId, exception.gameId)

        // Verify nothing changed.
        assertEquals(setOf("uk.co.rafearnold.captainsonar.game.$gameId"), redisClient.keys("*"))
        assertEquals(originalValue, redisClient.get("uk.co.rafearnold.captainsonar.game.$gameId"))

        // Verify the existing game is NOT deleted after the specified TTL.
        Thread.sleep(ttlMs)
        assertEquals(setOf("uk.co.rafearnold.captainsonar.game.$gameId"), redisClient.keys("*"))
        assertEquals(originalValue, redisClient.get("uk.co.rafearnold.captainsonar.game.$gameId"))
    }

    @Test
    fun `a game can be loaded`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
                objectMapper = objectMapper
            )

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        every { redisClientProvider.get() } returns redisClient

        val gameId = "test_gameId"
        redisClient.set(
            "uk.co.rafearnold.captainsonar.game.$gameId",
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"},"test_playerId2":{"name":"test_playerName2"},"test_playerId3":{"name":"test_playerName3"}},"started":false}"""
        )

        val actualResult: StoredGame? = repository.loadGame(gameId = gameId)
        val expectedResult =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                    "test_playerId3" to StoredPlayer(name = "test_playerName3"),
                ),
                started = false
            )
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `when a game is loaded that does not exist then null is returned`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
                objectMapper = objectMapper
            )

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        every { redisClientProvider.get() } returns redisClient

        val gameId = "test_gameId"
        val actualResult: StoredGame? = repository.loadGame(gameId = gameId)
        assertNull(actualResult)
    }

    @Test
    fun `a game can be updated`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
                objectMapper = objectMapper
            )

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        every { redisClientProvider.get() } returns redisClient

        val gameId = "test_gameId"
        redisClient.set(
            "uk.co.rafearnold.captainsonar.game.$gameId",
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"}},"started":false}"""
        )

        val updateOperations: List<UpdateStoredGameOperation> =
            listOf(
                AddPlayerOperation(playerId = "test_playerId2", player = StoredPlayer(name = "test_playerName2")),
                SetStartedOperation(started = true),
                AddPlayerOperation(playerId = "test_playerId3", player = StoredPlayer(name = "test_playerName3")),
            )
        val ttlMs: Long = 100
        val actualResult: StoredGame =
            repository.updateGame(
                gameId = gameId,
                updateOperations = updateOperations,
                ttl = ttlMs,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        val expectedResult =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                    "test_playerId3" to StoredPlayer(name = "test_playerName3"),
                ),
                started = true
            )
        assertEquals(expectedResult, actualResult)
        val actualRedisResult: String = redisClient.get("uk.co.rafearnold.captainsonar.game.$gameId")
        val expectedRedisResult =
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"},"test_playerId2":{"name":"test_playerName2"},"test_playerId3":{"name":"test_playerName3"}},"started":true}"""
        assertEquals(expectedRedisResult, actualRedisResult)

        // Verify the game is deleted after the specified TTL.
        Thread.sleep(ttlMs)
        assertNull(redisClient.get("uk.co.rafearnold.captainsonar.game.$gameId"))
    }

    @Test
    fun `when a game is updated that does not exist then an exception is thrown and no operation is performed`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
                objectMapper = objectMapper
            )

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        every { redisClientProvider.get() } returns redisClient

        val gameId = "test_gameId"
        val updateOperations: List<UpdateStoredGameOperation> =
            listOf(
                AddPlayerOperation(playerId = "test_playerId2", player = StoredPlayer(name = "test_playerName2")),
                SetStartedOperation(started = true),
                AddPlayerOperation(playerId = "test_playerId3", player = StoredPlayer(name = "test_playerName3")),
            )
        val ttlMs: Long = 100
        val exception: NoSuchGameFoundException =
            assertThrows {
                repository.updateGame(
                    gameId = gameId,
                    updateOperations = updateOperations,
                    ttl = ttlMs,
                    ttlUnit = TimeUnit.MILLISECONDS,
                )
            }
        assertEquals(gameId, exception.gameId)

        // Verify nothing changed.
        assertEquals(setOf<String>(), redisClient.keys("*"))
    }

    @Test
    fun `a game can be deleted`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
                objectMapper = objectMapper
            )

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        every { redisClientProvider.get() } returns redisClient

        val gameId = "test_gameId"
        redisClient.set(
            "uk.co.rafearnold.captainsonar.game.$gameId",
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"},"test_playerId2":{"name":"test_playerName2"},"test_playerId3":{"name":"test_playerName3"}},"started":false}"""
        )
        val key2 = "uk.co.rafearnold.captainsonar.game.test_otherGameId"
        redisClient.set(
            key2,
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"},"test_playerId2":{"name":"test_playerName2"},"test_playerId3":{"name":"test_playerName3"}},"started":false}"""
        )
        val key3 = "test_randomKey"
        redisClient.set(
            key3,
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"},"test_playerId2":{"name":"test_playerName2"},"test_playerId3":{"name":"test_playerName3"}},"started":false}"""
        )

        val actualResult: StoredGame? = repository.deleteGame(gameId = gameId)
        val expectedResult =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                    "test_playerId3" to StoredPlayer(name = "test_playerName3"),
                ),
                started = false
            )
        assertEquals(expectedResult, actualResult)
        assertEquals(setOf(key2, key3), redisClient.keys("*"))
    }

    @Test
    fun `when a game is deleted that does not exist then null is returned`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
                objectMapper = objectMapper
            )

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        every { redisClientProvider.get() } returns redisClient

        val gameId = "test_gameId"
        val actualResult: StoredGame? = repository.deleteGame(gameId = gameId)
        assertNull(actualResult)
    }
}
