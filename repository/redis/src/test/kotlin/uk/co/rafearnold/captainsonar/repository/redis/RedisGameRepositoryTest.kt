package uk.co.rafearnold.captainsonar.repository.redis

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.vertx.core.buffer.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
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

        val actualResult: ByteArray = redisClient.get("uk.co.rafearnold.captainsonar.game.$gameId".toByteArray())
        val expectedResult: ByteArray =
            serializeGameV1(
                hostId = storedGame.hostId,
                players = storedGame.players.mapValues { it.value.name },
                started = storedGame.started,
            )
        assertThat(actualResult).containsExactly(expectedResult.toTypedArray())

        // Verify the game is deleted after the specified TTL.
        Thread.sleep(ttlMs)
        assertThat(redisClient.get("uk.co.rafearnold.captainsonar.game.$gameId")).isNull()
    }

    @Test
    fun `when a game is created with the same id as an existing game then an exception is thrown and no operation is performed`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
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
        assertThatExceptionOfType(GameAlreadyExistsException::class.java)
            .isThrownBy {
                repository
                    .createGame(gameId = gameId, game = storedGame, ttl = ttlMs, ttlUnit = TimeUnit.MILLISECONDS)
            }
            .isEqualTo(GameAlreadyExistsException(gameId = gameId))

        // Verify nothing changed.
        assertThat(redisClient.keys("*")).containsOnly("uk.co.rafearnold.captainsonar.game.$gameId")
        assertThat(redisClient.get("uk.co.rafearnold.captainsonar.game.$gameId")).isEqualTo(originalValue)

        // Verify the existing game is NOT deleted after the specified TTL.
        Thread.sleep(ttlMs)
        assertThat(redisClient.keys("*")).containsOnly("uk.co.rafearnold.captainsonar.game.$gameId")
        assertThat(redisClient.get("uk.co.rafearnold.captainsonar.game.$gameId")).isEqualTo(originalValue)
    }

    @Test
    fun `a game can be loaded`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
            )

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        every { redisClientProvider.get() } returns redisClient

        val gameId = "test_gameId"
        val game =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                    "test_playerId3" to StoredPlayer(name = "test_playerName3"),
                ),
                started = false,
            )
        redisClient.set(
            "uk.co.rafearnold.captainsonar.game.$gameId".toByteArray(),
            serializeGameV1(
                hostId = game.hostId,
                players = game.players.mapValues { it.value.name },
                started = game.started,
            ),
        )

        val actualResult: StoredGame? = repository.loadGame(gameId = gameId)
        assertThat(actualResult).isEqualTo(game)
    }

    @Test
    fun `when a game is loaded that does not exist then null is returned`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
            )

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        every { redisClientProvider.get() } returns redisClient

        val gameId = "test_gameId"
        val actualResult: StoredGame? = repository.loadGame(gameId = gameId)
        assertThat(actualResult).isNull()
    }

    @Test
    fun `a game can be updated`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
            )

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        every { redisClientProvider.get() } returns redisClient

        val gameId = "test_gameId"
        val originalGame =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf("test_playerId1" to StoredPlayer(name = "test_playerName1")),
                started = false,
            )
        redisClient.set(
            "uk.co.rafearnold.captainsonar.game.$gameId".toByteArray(),
            serializeGameV1(
                hostId = originalGame.hostId,
                players = originalGame.players.mapValues { it.value.name },
                started = originalGame.started,
            ),
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
        assertThat(actualResult).isEqualTo(expectedResult)
        val actualRedisResult: ByteArray = redisClient.get("uk.co.rafearnold.captainsonar.game.$gameId".toByteArray())
        val expectedRedisResult: ByteArray =
            serializeGameV1(
                hostId = expectedResult.hostId,
                players = expectedResult.players.mapValues { it.value.name },
                started = expectedResult.started,
            )
        assertThat(expectedRedisResult).containsExactly(actualRedisResult.toTypedArray())

        // Verify the game is deleted after the specified TTL.
        Thread.sleep(ttlMs)
        assertThat(redisClient.get("uk.co.rafearnold.captainsonar.game.$gameId")).isNull()
    }

    @Test
    fun `when a game is updated that does not exist then an exception is thrown and no operation is performed`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
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
        assertThatExceptionOfType(NoSuchGameFoundException::class.java)
            .isThrownBy {
                repository.updateGame(
                    gameId = gameId,
                    updateOperations = updateOperations,
                    ttl = ttlMs,
                    ttlUnit = TimeUnit.MILLISECONDS,
                )
            }
            .isEqualTo(NoSuchGameFoundException(gameId = gameId))

        // Verify nothing changed.
        assertThat(redisClient.keys("*")).isEmpty()
    }

    @Test
    fun `a game can be deleted`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
            )

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        every { redisClientProvider.get() } returns redisClient

        val gameId = "test_gameId"
        val game =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                    "test_playerId3" to StoredPlayer(name = "test_playerName3"),
                ),
                started = false,
            )
        redisClient.set(
            "uk.co.rafearnold.captainsonar.game.$gameId".toByteArray(),
            serializeGameV1(
                hostId = game.hostId,
                players = game.players.mapValues { it.value.name },
                started = game.started,
            ),
        )
        val key2 = "uk.co.rafearnold.captainsonar.game.test_otherGameId"
        redisClient.set(
            key2.toByteArray(),
            serializeGameV1(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to "test_playerName1",
                    "test_playerId2" to "test_playerName2",
                    "test_playerId3" to "test_playerName3",
                ),
                started = false,
            ),
        )
        val key3 = "test_randomKey"
        redisClient.set(key3, "")

        val actualResult: StoredGame? = repository.deleteGame(gameId = gameId)
        assertThat(actualResult).isEqualTo(game)
        assertThat(redisClient.keys("*")).containsOnly(key2, key3)
    }

    @Test
    fun `when a game is deleted that does not exist then null is returned`() {
        val redisClientProvider: RedisClientProvider = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository =
            RedisGameRepository(
                redisClientProvider = redisClientProvider,
                sharedDataService = sharedDataService,
            )

        val redisClient = Jedis(redisContainer.host, redisContainer.firstMappedPort)
        every { redisClientProvider.get() } returns redisClient

        val gameId = "test_gameId"
        val actualResult: StoredGame? = repository.deleteGame(gameId = gameId)
        assertThat(actualResult).isNull()
    }

    private fun serializeGameV1(hostId: String, players: Map<String, String>, started: Boolean): ByteArray {
        val buffer: Buffer = Buffer.buffer()
        val hostIdBytes: ByteArray = hostId.toByteArray(Charsets.UTF_8)
        buffer.appendInt(hostIdBytes.size).appendBytes(hostIdBytes)
        buffer.appendInt(players.size)
        for ((playerId: String, playerName: String) in players) {
            val playerIdBytes: ByteArray = playerId.toByteArray(Charsets.UTF_8)
            buffer.appendInt(playerIdBytes.size).appendBytes(playerIdBytes)
            val playerNameBytes: ByteArray = playerName.toByteArray(Charsets.UTF_8)
            buffer.appendInt(playerNameBytes.size).appendBytes(playerNameBytes)
        }
        buffer.appendByte(if (started) 1 else 0)
        return buffer.bytes
    }
}
