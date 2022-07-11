package uk.co.rafearnold.captainsonar.repository.shareddata

import com.hazelcast.config.Config
import com.hazelcast.config.SerializationConfig
import com.hazelcast.config.SerializerConfig
import com.hazelcast.core.Hazelcast
import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import io.vertx.core.shareddata.ClusterSerializable
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.co.rafearnold.captainsonar.common.GameAlreadyExistsException
import uk.co.rafearnold.captainsonar.common.NoSuchGameFoundException
import uk.co.rafearnold.captainsonar.repository.AddPlayerOperation
import uk.co.rafearnold.captainsonar.repository.SetStartedOperation
import uk.co.rafearnold.captainsonar.repository.StoredGame
import uk.co.rafearnold.captainsonar.repository.StoredGameSerializableHolder
import uk.co.rafearnold.captainsonar.repository.StoredPlayer
import uk.co.rafearnold.captainsonar.repository.UpdateStoredGameOperation
import uk.co.rafearnold.commons.shareddata.SharedDataService
import uk.co.rafearnold.commons.shareddata.SharedMap
import uk.co.rafearnold.commons.shareddata.getDistributedMap
import uk.co.rafearnold.commons.shareddata.hazelcast.HazelcastClusterSerializableSerializer
import uk.co.rafearnold.commons.shareddata.hazelcast.HazelcastSharedDataService
import uk.co.rafearnold.commons.shareddata.simple.SimpleClusterManager
import java.util.concurrent.TimeUnit

class SharedDataGameRepositoryTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
        SimpleClusterManager.clearAllClusters()
    }

    @Test
    fun `a game can be created`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService)

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
        val ttlMs: Long = 10
        repository.createGame(gameId = gameId, game = storedGame, ttl = ttlMs, ttlUnit = TimeUnit.MILLISECONDS)

        val dataMap: SharedMap<String, StoredGameSerializableHolder> =
            sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")
        val actualResult: StoredGameSerializableHolder? = dataMap["uk.co.rafearnold.captainsonar.game.$gameId"]
        assertThat(actualResult?.storedGame).isEqualTo(storedGame)

        // Verify the game is deleted after the specified TTL.
        Thread.sleep(2 * ttlMs)
        assertThat(dataMap["uk.co.rafearnold.captainsonar.game.$gameId"]).isNull()
    }

    @Test
    fun `when a game is created with the same id as an existing game then an exception is thrown and no operation is performed`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService)

        val gameId = "test_gameId"

        val dataMap: SharedMap<String, StoredGameSerializableHolder> =
            sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")
        val originalValue =
            StoredGameSerializableHolder
                .create(storedGame = StoredGame(hostId = "test_otherHostId", players = mapOf(), started = true))
        dataMap["uk.co.rafearnold.captainsonar.game.$gameId"] = originalValue

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
        val ttlMs: Long = 10
        assertThatExceptionOfType(GameAlreadyExistsException::class.java)
            .isThrownBy {
                repository.createGame(gameId = gameId, game = storedGame, ttl = ttlMs, ttlUnit = TimeUnit.MILLISECONDS)
            }
            .isEqualTo(GameAlreadyExistsException(gameId = gameId))

        // Verify nothing changed.
        assertThat(dataMap.keys).containsOnly("uk.co.rafearnold.captainsonar.game.$gameId")
        assertThat(dataMap["uk.co.rafearnold.captainsonar.game.$gameId"]).isEqualTo(originalValue)

        // Verify the existing game is NOT deleted after the specified TTL.
        Thread.sleep(2 * ttlMs)
        assertThat(dataMap.keys).containsOnly("uk.co.rafearnold.captainsonar.game.$gameId")
        assertThat(dataMap["uk.co.rafearnold.captainsonar.game.$gameId"]).isEqualTo(originalValue)
    }

    @Test
    fun `a game can be loaded`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService)

        val gameId = "test_gameId"
        val dataMap: SharedMap<String, StoredGameSerializableHolder> =
            sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")
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
        dataMap["uk.co.rafearnold.captainsonar.game.$gameId"] = StoredGameSerializableHolder.create(storedGame = game)

        val actualResult: StoredGame? = repository.loadGame(gameId = gameId)
        assertThat(actualResult).isEqualTo(game)
    }

    @Test
    fun `when a game is loaded that does not exist then null is returned`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService)

        val gameId = "test_gameId"
        val actualResult: StoredGame? = repository.loadGame(gameId = gameId)
        assertThat(actualResult).isNull()
    }

    @Test
    fun `a game can be updated`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService)

        val gameId = "test_gameId"
        val dataMap: SharedMap<String, StoredGameSerializableHolder> =
            sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")
        dataMap["uk.co.rafearnold.captainsonar.game.$gameId"] =
            StoredGameSerializableHolder.create(
                storedGame = StoredGame(
                    hostId = "test_hostId",
                    players = mapOf("test_playerId1" to StoredPlayer(name = "test_playerName1")),
                    started = false
                )
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
        val actualDataMapResult: StoredGameSerializableHolder? = dataMap["uk.co.rafearnold.captainsonar.game.$gameId"]
        assertThat(actualDataMapResult?.storedGame).isEqualTo(expectedResult)

        // Verify the game is deleted after the specified TTL.
        Thread.sleep(2 * ttlMs)
        assertThat(dataMap["uk.co.rafearnold.captainsonar.game.$gameId"]).isNull()
    }

    @Test
    fun `when a game is updated that does not exist then an exception is thrown and no operation is performed`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService)

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
        val dataMap: SharedMap<String, String> =
            sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")
        assertThat(dataMap.keys).isEmpty()
    }

    @Test
    fun `a game can be deleted`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService)

        val gameId = "test_gameId"
        val dataMap: SharedMap<String, StoredGameSerializableHolder> =
            sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")
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
        dataMap["uk.co.rafearnold.captainsonar.game.$gameId"] = StoredGameSerializableHolder.create(storedGame = game)
        val key2 = "uk.co.rafearnold.captainsonar.game.test_otherGameId"
        dataMap[key2] =
            StoredGameSerializableHolder.create(
                storedGame = StoredGame(
                    hostId = "test_hostId",
                    players = mapOf(
                        "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                        "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                        "test_playerId3" to StoredPlayer(name = "test_playerName3"),
                    ),
                    started = false,
                ),
            )
        val key3 = "test_randomKey"
        dataMap[key3] =
            StoredGameSerializableHolder.create(
                storedGame = StoredGame(
                    hostId = "test_hostId",
                    players = mapOf(
                        "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                        "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                        "test_playerId3" to StoredPlayer(name = "test_playerName3"),
                    ),
                    started = false,
                ),
            )

        val actualResult: StoredGame? = repository.deleteGame(gameId = gameId)
        assertThat(actualResult).isEqualTo(game)
        assertThat(dataMap.keys).containsOnly(key2, key3)
    }

    @Test
    fun `when a game is deleted that does not exist then null is returned`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService)

        val gameId = "test_gameId"
        val actualResult: StoredGame? = repository.deleteGame(gameId = gameId)
        assertThat(actualResult).isNull()
    }

    @Test
    fun `hazelcast serializes games correctly`() {
        val serializerConfig: SerializerConfig =
            SerializerConfig()
                .setClass(HazelcastClusterSerializableSerializer::class.java)
                .setTypeClass(ClusterSerializable::class.java)
        val hazelcastConfig: Config =
            Config().setSerializationConfig(SerializationConfig().addSerializerConfig(serializerConfig))
        val sharedDataService: SharedDataService =
            HazelcastSharedDataService(Hazelcast.newHazelcastInstance(hazelcastConfig))
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService)

        // This has to be at least one second since hazelcast rounds down to the nearest second.
        val ttlMs: Long = 1000
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
        repository.createGame(gameId = gameId, game = game, ttl = ttlMs, ttlUnit = TimeUnit.MILLISECONDS)
        assertThat(repository.loadGame(gameId = gameId)).isEqualTo(game)
        Thread.sleep(ttlMs)
        assertThat(repository.loadGame(gameId = gameId)).isNull()
    }
}
