package uk.co.rafearnold.captainsonar.repository.shareddata

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.mockk.clearAllMocks
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.co.rafearnold.captainsonar.common.GameAlreadyExistsException
import uk.co.rafearnold.captainsonar.common.NoSuchGameFoundException
import uk.co.rafearnold.captainsonar.repository.AddPlayerOperation
import uk.co.rafearnold.captainsonar.repository.SetStartedOperation
import uk.co.rafearnold.captainsonar.repository.StoredGame
import uk.co.rafearnold.captainsonar.repository.StoredPlayer
import uk.co.rafearnold.captainsonar.repository.UpdateStoredGameOperation
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import uk.co.rafearnold.captainsonar.shareddata.SharedMap
import uk.co.rafearnold.captainsonar.shareddata.simple.SimpleClusterManager
import uk.co.rafearnold.captainsonar.shareddata.getDistributedMap
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
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService, objectMapper = objectMapper)

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

        val dataMap: SharedMap<String, String> =
            sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")
        val actualResult: String? = dataMap["uk.co.rafearnold.captainsonar.game.$gameId"]
        val expectedResult =
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"},"test_playerId2":{"name":"test_playerName2"},"test_playerId3":{"name":"test_playerName3"}},"started":false}"""
        assertEquals(expectedResult, actualResult)

        // Verify the game is deleted after the specified TTL.
        Thread.sleep(2 * ttlMs)
        assertNull(dataMap["uk.co.rafearnold.captainsonar.game.$gameId"])
    }

    @Test
    fun `when a game is created with the same id as an existing game then an exception is thrown and no operation is performed`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService, objectMapper = objectMapper)

        val gameId = "test_gameId"

        val dataMap: SharedMap<String, String> =
            sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")
        val originalValue = "test_originalValue"
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
        val exception: GameAlreadyExistsException =
            assertThrows {
                repository.createGame(gameId = gameId, game = storedGame, ttl = ttlMs, ttlUnit = TimeUnit.MILLISECONDS)
            }
        assertEquals(gameId, exception.gameId)

        // Verify nothing changed.
        assertEquals(setOf("uk.co.rafearnold.captainsonar.game.$gameId"), dataMap.keys)
        assertEquals(originalValue, dataMap["uk.co.rafearnold.captainsonar.game.$gameId"])

        // Verify the existing game is NOT deleted after the specified TTL.
        Thread.sleep(2 * ttlMs)
        assertEquals(setOf("uk.co.rafearnold.captainsonar.game.$gameId"), dataMap.keys)
        assertEquals(originalValue, dataMap["uk.co.rafearnold.captainsonar.game.$gameId"])
    }

    @Test
    fun `a game can be loaded`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService, objectMapper = objectMapper)

        val gameId = "test_gameId"
        val dataMap: SharedMap<String, String> =
            sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")
        dataMap["uk.co.rafearnold.captainsonar.game.$gameId"] =
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"},"test_playerId2":{"name":"test_playerName2"},"test_playerId3":{"name":"test_playerName3"}},"started":false}"""

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
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService, objectMapper = objectMapper)

        val gameId = "test_gameId"
        val actualResult: StoredGame? = repository.loadGame(gameId = gameId)
        assertNull(actualResult)
    }

    @Test
    fun `a game can be updated`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService, objectMapper = objectMapper)

        val gameId = "test_gameId"
        val dataMap: SharedMap<String, String> =
            sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")
        dataMap["uk.co.rafearnold.captainsonar.game.$gameId"] =
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"}},"started":false}"""

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
        val actualRedisResult: String? = dataMap["uk.co.rafearnold.captainsonar.game.$gameId"]
        val expectedRedisResult =
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"},"test_playerId2":{"name":"test_playerName2"},"test_playerId3":{"name":"test_playerName3"}},"started":true}"""
        assertEquals(expectedRedisResult, actualRedisResult)

        // Verify the game is deleted after the specified TTL.
        Thread.sleep(2 * ttlMs)
        assertNull(dataMap["uk.co.rafearnold.captainsonar.game.$gameId"])
    }

    @Test
    fun `when a game is updated that does not exist then an exception is thrown and no operation is performed`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService, objectMapper = objectMapper)

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
        val dataMap: SharedMap<String, String> =
            sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")
        assertEquals(setOf<String>(), dataMap.keys)
    }

    @Test
    fun `a game can be deleted`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService, objectMapper = objectMapper)

        val gameId = "test_gameId"
        val dataMap: SharedMap<String, String> =
            sharedDataService.getDistributedMap("uk.co.rafearnold.captainsonar.repository.shared-data.data-map")
        dataMap["uk.co.rafearnold.captainsonar.game.$gameId"] =
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"},"test_playerId2":{"name":"test_playerName2"},"test_playerId3":{"name":"test_playerName3"}},"started":false}"""
        val key2 = "uk.co.rafearnold.captainsonar.game.test_otherGameId"
        dataMap[key2] =
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"},"test_playerId2":{"name":"test_playerName2"},"test_playerId3":{"name":"test_playerName3"}},"started":false}"""
        val key3 = "test_randomKey"
        dataMap[key3] =
            """{"hostId":"test_hostId","players":{"test_playerId1":{"name":"test_playerName1"},"test_playerId2":{"name":"test_playerName2"},"test_playerId3":{"name":"test_playerName3"}},"started":false}"""

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
        assertEquals(setOf(key2, key3), dataMap.keys)
    }

    @Test
    fun `when a game is deleted that does not exist then null is returned`() {
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val objectMapper: ObjectMapper = jacksonObjectMapper()
        val repository = SharedDataGameRepository(sharedDataService = sharedDataService, objectMapper = objectMapper)

        val gameId = "test_gameId"
        val actualResult: StoredGame? = repository.deleteGame(gameId = gameId)
        assertNull(actualResult)
    }
}
