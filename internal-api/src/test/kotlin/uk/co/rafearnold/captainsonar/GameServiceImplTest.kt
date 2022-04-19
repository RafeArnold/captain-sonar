package uk.co.rafearnold.captainsonar

import io.mockk.CapturingSlot
import io.mockk.Ordering
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import uk.co.rafearnold.captainsonar.common.GameAlreadyStartedException
import uk.co.rafearnold.captainsonar.common.NoSuchGameFoundException
import uk.co.rafearnold.captainsonar.common.PlayerAlreadyJoinedGameException
import uk.co.rafearnold.captainsonar.common.UserIsNotHostException
import uk.co.rafearnold.captainsonar.config.ObservableMap
import uk.co.rafearnold.captainsonar.config.ObservableMutableMap
import uk.co.rafearnold.captainsonar.config.ObservableMutableMapImpl
import uk.co.rafearnold.captainsonar.eventapi.v1.EventApiV1Service
import uk.co.rafearnold.captainsonar.eventapi.v1.GameEventEventApiV1Handler
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEndedEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameStartedEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.PlayerAddedEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.PlayerEventApiV1Model
import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.GameEndedEventImpl
import uk.co.rafearnold.captainsonar.model.GameEvent
import uk.co.rafearnold.captainsonar.model.GameImpl
import uk.co.rafearnold.captainsonar.model.GameStartedEventImpl
import uk.co.rafearnold.captainsonar.model.PlayerAddedEventImpl
import uk.co.rafearnold.captainsonar.model.PlayerImpl
import uk.co.rafearnold.captainsonar.model.factory.GameEventFactoryImpl
import uk.co.rafearnold.captainsonar.model.factory.GameFactoryImpl
import uk.co.rafearnold.captainsonar.model.factory.PlayerFactoryImpl
import uk.co.rafearnold.captainsonar.model.mapper.ModelMapperImpl
import uk.co.rafearnold.captainsonar.repository.AddPlayerOperation
import uk.co.rafearnold.captainsonar.repository.GameRepository
import uk.co.rafearnold.captainsonar.repository.SetStartedOperation
import uk.co.rafearnold.captainsonar.repository.StoredGame
import uk.co.rafearnold.captainsonar.repository.StoredPlayer
import uk.co.rafearnold.captainsonar.repository.UpdateStoredGameOperation
import uk.co.rafearnold.captainsonar.shareddata.SharedDataService
import uk.co.rafearnold.captainsonar.shareddata.SimpleClusterManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GameServiceImplTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
        SimpleClusterManager.clearAllClusters()
    }

    @Test
    @Suppress("ControlFlowWithEmptyBody")
    fun `when the service is registered then the service subscribes to the game events of the event api`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = mockk()
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val eventHandlerSlot: CapturingSlot<GameEventEventApiV1Handler> = slot()
        justRun { eventApiService.subscribeToGameEvents(capture(eventHandlerSlot)) }

        gameService.register().get(2, TimeUnit.SECONDS)

        verify(ordering = Ordering.SEQUENCE) {
            eventApiService.subscribeToGameEvents(any())
        }
        confirmVerified(eventApiService)

        // Test that game events are sent to listeners.
        val gameId1 = "test_gameId1"
        val gameId2 = "test_gameId2"
        val listener1Events: MutableSet<GameEvent> = ConcurrentHashMap.newKeySet()
        gameService.addGameListener(gameId1) { listener1Events.add(it) }
        val listener2Events: MutableSet<GameEvent> = ConcurrentHashMap.newKeySet()
        gameService.addGameListener(gameId2) { listener2Events.add(it) }

        val eventApiEvent1: GameEventEventApiV1Model =
            PlayerAddedEventEventApiV1Model(
                gameId = gameId1,
                game = GameEventApiV1Model(
                    hostId = "test_hostId1",
                    players = mapOf(
                        "test_playerId1" to PlayerEventApiV1Model(name = "test_playerName1"),
                        "test_playerId2" to PlayerEventApiV1Model(name = "test_playerName2"),
                    ),
                    started = false,
                ),
            )
        eventHandlerSlot.captured.handle(event = eventApiEvent1)

        val expectedListener1Events1: Set<GameEvent> =
            setOf(
                PlayerAddedEventImpl(
                    game = GameImpl(
                        id = gameId1,
                        hostId = "test_hostId1",
                        players = mapOf(
                            "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                            "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                        ),
                        started = false,
                    ),
                ),
            )
        CompletableFuture.runAsync { while (listener1Events.size != expectedListener1Events1.size); }
            .get(2, TimeUnit.SECONDS)
        assertEquals(expectedListener1Events1, listener1Events)
        CompletableFuture.runAsync { while (listener2Events.size != 0); }
            .get(2, TimeUnit.SECONDS)
        assertEquals(setOf<GameEvent>(), listener2Events)

        val eventApiEvent2: GameEventEventApiV1Model =
            GameStartedEventEventApiV1Model(
                gameId = gameId2,
                game = GameEventApiV1Model(
                    hostId = "test_hostId2",
                    players = mapOf(
                        "test_playerId3" to PlayerEventApiV1Model(name = "test_playerName3"),
                        "test_playerId4" to PlayerEventApiV1Model(name = "test_playerName4"),
                    ),
                    started = true,
                ),
            )
        eventHandlerSlot.captured.handle(event = eventApiEvent2)

        val expectedListener1Events2: Set<GameEvent> =
            setOf(
                PlayerAddedEventImpl(
                    game = GameImpl(
                        id = gameId1,
                        hostId = "test_hostId1",
                        players = mapOf(
                            "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                            "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                        ),
                        started = false,
                    ),
                ),
            )
        CompletableFuture.runAsync { while (listener1Events.size != expectedListener1Events2.size); }
            .get(2, TimeUnit.SECONDS)
        assertEquals(expectedListener1Events2, listener1Events)
        val expectedListener2Events2: Set<GameEvent> =
            setOf(
                GameStartedEventImpl(
                    game = GameImpl(
                        id = gameId2,
                        hostId = "test_hostId2",
                        players = mapOf(
                            "test_playerId3" to PlayerImpl(name = "test_playerName3"),
                            "test_playerId4" to PlayerImpl(name = "test_playerName4"),
                        ),
                        started = true,
                    ),
                ),
            )
        CompletableFuture.runAsync { while (listener2Events.size != expectedListener2Events2.size); }
            .get(2, TimeUnit.SECONDS)
        assertEquals(expectedListener2Events2, listener2Events)

        val eventApiEvent3: GameEventEventApiV1Model =
            GameEndedEventEventApiV1Model(gameId = gameId1)
        eventHandlerSlot.captured.handle(event = eventApiEvent3)

        val expectedListener1Events3: Set<GameEvent> =
            setOf(
                PlayerAddedEventImpl(
                    game = GameImpl(
                        id = gameId1,
                        hostId = "test_hostId1",
                        players = mapOf(
                            "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                            "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                        ),
                        started = false,
                    ),
                ),
                GameEndedEventImpl,
            )
        CompletableFuture.runAsync { while (listener1Events.size != expectedListener1Events3.size); }
            .get(2, TimeUnit.SECONDS)
        assertEquals(expectedListener1Events3, listener1Events)
        val expectedListener2Events3: Set<GameEvent> =
            setOf(
                GameStartedEventImpl(
                    game = GameImpl(
                        id = gameId2,
                        hostId = "test_hostId2",
                        players = mapOf(
                            "test_playerId3" to PlayerImpl(name = "test_playerName3"),
                            "test_playerId4" to PlayerImpl(name = "test_playerName4"),
                        ),
                        started = true,
                    ),
                ),
            )
        CompletableFuture.runAsync { while (listener2Events.size != expectedListener2Events3.size); }
            .get(2, TimeUnit.SECONDS)
        assertEquals(expectedListener2Events3, listener2Events)

        confirmVerified(gameIdGenerator, gameRepository)
    }

    @Test
    fun `a game can be retrieved by id`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = mockk()
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        every { gameRepository.loadGame(gameId = gameId) } returns StoredGame(
            hostId = "test_hostId",
            players = mapOf(
                "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                "test_playerId2" to StoredPlayer(name = "test_playerName2"),
            ),
            started = false,
        )

        val game: Game? = gameService.getGame(gameId = gameId)

        assertNotNull(game)
        assertEquals("test_hostId", game!!.hostId)
        assertEquals(2, game.players.size)
        assertEquals("test_playerName1", game.players["test_playerId1"]?.name)
        assertEquals("test_playerName2", game.players["test_playerId2"]?.name)
        assertFalse(game.started)

        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
        }
        confirmVerified(gameIdGenerator, gameRepository)
    }

    @Test
    fun `when a game that does not exist is retrieved then null is returned`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = mockk()
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        every { gameRepository.loadGame(gameId = gameId) } returns null

        val game: Game? = gameService.getGame(gameId = gameId)

        assertNull(game)

        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
        }
        confirmVerified(gameIdGenerator, gameRepository)
    }

    @Test
    fun `a game can be created`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = ObservableMutableMapImpl(ConcurrentHashMap())
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val hostId = "test_hostId"
        val hostName = "test_hostName"
        every { gameIdGenerator.generateId() } returns gameId
        val expectedStoredGame =
            StoredGame(hostId = hostId, players = mapOf(hostId to StoredPlayer(name = hostName)), started = false)
        every {
            gameRepository.createGame(
                gameId = gameId,
                game = expectedStoredGame,
                ttl = 3600000,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        } returns expectedStoredGame

        val actualResult: Game = gameService.createGame(hostId = hostId, hostName = hostName)

        val expectedResult: Game =
            GameImpl(
                id = gameId,
                hostId = hostId,
                players = mapOf(hostId to PlayerImpl(name = hostName)),
                started = false,
            )
        assertEquals(expectedResult, actualResult)
        verify(ordering = Ordering.SEQUENCE) {
            gameIdGenerator.generateId()
            gameRepository.createGame(
                gameId = gameId,
                game = expectedStoredGame,
                ttl = 3600000,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        }
        confirmVerified(gameIdGenerator, gameRepository)
    }

    @Test
    fun `the ttl when creating a game can be configured`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMutableMap<String, String> = ObservableMutableMapImpl(ConcurrentHashMap())
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val hostId = "test_hostId"
        val hostName = "test_hostName"
        every { gameIdGenerator.generateId() } returns gameId
        val expectedStoredGame =
            StoredGame(hostId = hostId, players = mapOf(hostId to StoredPlayer(name = hostName)), started = false)
        val ttlMs: Long = 234535
        appConfig["game.ttl.ms"] = ttlMs.toString()
        every {
            gameRepository.createGame(
                gameId = gameId,
                game = expectedStoredGame,
                ttl = ttlMs,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        } returns expectedStoredGame

        val actualResult: Game = gameService.createGame(hostId = hostId, hostName = hostName)

        val expectedResult: Game =
            GameImpl(
                id = gameId,
                hostId = hostId,
                players = mapOf(hostId to PlayerImpl(name = hostName)),
                started = false,
            )
        assertEquals(expectedResult, actualResult)
        verify(ordering = Ordering.SEQUENCE) {
            gameIdGenerator.generateId()
            gameRepository.createGame(
                gameId = gameId,
                game = expectedStoredGame,
                ttl = ttlMs,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        }
        confirmVerified(gameIdGenerator, gameRepository)
    }

    @Test
    fun `a player can be added to an existing game`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk(relaxed = true)
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = ObservableMutableMapImpl(ConcurrentHashMap())
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val newPlayerId = "test_newPlayerId"
        val newPlayerName = "test_newPlayerName"

        val originalStoredGame =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = false,
            )
        every { gameRepository.loadGame(gameId = gameId) } returns originalStoredGame
        val expectedUpdateOperations: List<UpdateStoredGameOperation> =
            listOf(AddPlayerOperation(playerId = newPlayerId, player = StoredPlayer(name = newPlayerName)))
        val newStoredGame =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                    newPlayerId to StoredPlayer(name = newPlayerName),
                ),
                started = false,
            )
        every {
            gameRepository.updateGame(
                gameId = gameId,
                updateOperations = expectedUpdateOperations,
                ttl = 3600000,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        } returns newStoredGame

        val actualResult: Game =
            gameService.addPlayer(gameId = gameId, playerId = newPlayerId, playerName = newPlayerName)

        val expectedResult: Game =
            GameImpl(
                id = gameId,
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                    "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                    newPlayerId to PlayerImpl(name = newPlayerName),
                ),
                started = false,
            )
        assertEquals(expectedResult, actualResult)
        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
            gameRepository.updateGame(
                gameId = gameId,
                updateOperations = expectedUpdateOperations,
                ttl = 3600000,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        }
        confirmVerified(gameIdGenerator, gameRepository)
    }

    @Test
    fun `the ttl when adding a player can be configured`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk(relaxed = true)
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMutableMap<String, String> = ObservableMutableMapImpl(ConcurrentHashMap())
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val newPlayerId = "test_newPlayerId"
        val newPlayerName = "test_newPlayerName"

        val originalStoredGame =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = false,
            )
        every { gameRepository.loadGame(gameId = gameId) } returns originalStoredGame
        val expectedUpdateOperations: List<UpdateStoredGameOperation> =
            listOf(AddPlayerOperation(playerId = newPlayerId, player = StoredPlayer(name = newPlayerName)))
        val newStoredGame =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                    newPlayerId to StoredPlayer(name = newPlayerName),
                ),
                started = false,
            )
        val ttlMs: Long = 43647
        appConfig["game.ttl.ms"] = ttlMs.toString()
        every {
            gameRepository.updateGame(
                gameId = gameId,
                updateOperations = expectedUpdateOperations,
                ttl = ttlMs,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        } returns newStoredGame

        val actualResult: Game =
            gameService.addPlayer(gameId = gameId, playerId = newPlayerId, playerName = newPlayerName)

        val expectedResult: Game =
            GameImpl(
                id = gameId,
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                    "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                    newPlayerId to PlayerImpl(name = newPlayerName),
                ),
                started = false,
            )
        assertEquals(expectedResult, actualResult)
        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
            gameRepository.updateGame(
                gameId = gameId,
                updateOperations = expectedUpdateOperations,
                ttl = ttlMs,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        }
        confirmVerified(gameIdGenerator, gameRepository)
    }

    @Test
    fun `when a player is added to a game then an event is published`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = ObservableMutableMapImpl(ConcurrentHashMap())
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val newPlayerId = "test_newPlayerId"
        val newPlayerName = "test_newPlayerName"

        val originalStoredGame =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = false,
            )
        every { gameRepository.loadGame(gameId = gameId) } returns originalStoredGame
        val expectedUpdateOperations: List<UpdateStoredGameOperation> =
            listOf(AddPlayerOperation(playerId = newPlayerId, player = StoredPlayer(name = newPlayerName)))
        val newStoredGame =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                    newPlayerId to StoredPlayer(name = newPlayerName),
                ),
                started = false,
            )
        every {
            gameRepository.updateGame(
                gameId = gameId,
                updateOperations = expectedUpdateOperations,
                ttl = 3600000,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        } returns newStoredGame
        val expectedEvent: GameEventEventApiV1Model =
            PlayerAddedEventEventApiV1Model(
                gameId = gameId,
                game = GameEventApiV1Model(
                    hostId = "test_hostId",
                    players = mapOf(
                        "test_playerId1" to PlayerEventApiV1Model(name = "test_playerName1"),
                        "test_playerId2" to PlayerEventApiV1Model(name = "test_playerName2"),
                        newPlayerId to PlayerEventApiV1Model(name = newPlayerName),
                    ),
                    started = false,
                ),
            )
        justRun { eventApiService.publishGameEvent(event = expectedEvent) }

        gameService.addPlayer(gameId = gameId, playerId = newPlayerId, playerName = newPlayerName)

        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
            gameRepository.updateGame(
                gameId = gameId,
                updateOperations = expectedUpdateOperations,
                ttl = 3600000,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
            eventApiService.publishGameEvent(event = expectedEvent)
        }
        confirmVerified(gameIdGenerator, gameRepository, eventApiService)
    }

    @Test
    fun `when a player is added to a game that does not exist then an exception is thrown and no operation is performed`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk(relaxed = true)
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = mockk()
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val newPlayerId = "test_newPlayerId"
        val newPlayerName = "test_newPlayerName"

        every { gameRepository.loadGame(gameId = gameId) } returns null

        val exception: NoSuchGameFoundException =
            assertThrows { gameService.addPlayer(gameId = gameId, playerId = newPlayerId, playerName = newPlayerName) }

        assertEquals(gameId, exception.gameId)
        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
        }
        confirmVerified(gameIdGenerator, gameRepository, eventApiService)
    }

    @Test
    fun `when a player is added to a game they have already joined then an exception is thrown and no operation is performed`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk(relaxed = true)
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = mockk()
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val existingPlayerId = "test_newPlayerId"
        val newPlayerName = "test_newPlayerName"

        val originalStoredGame =
            StoredGame(
                hostId = "test_hostId",
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    existingPlayerId to StoredPlayer(name = newPlayerName),
                ),
                started = false,
            )
        every { gameRepository.loadGame(gameId = gameId) } returns originalStoredGame

        val exception: PlayerAlreadyJoinedGameException =
            assertThrows {
                gameService.addPlayer(gameId = gameId, playerId = existingPlayerId, playerName = newPlayerName)
            }

        assertEquals(gameId, exception.gameId)
        assertEquals(existingPlayerId, exception.playerId)
        assertEquals(newPlayerName, exception.playerName)
        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
        }
        confirmVerified(gameIdGenerator, gameRepository, eventApiService)
    }

    @Test
    fun `an existing game that has not yet started can be started`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk(relaxed = true)
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = ObservableMutableMapImpl(ConcurrentHashMap())
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val hostId = "test_hostId"
        val originalStoredGame =
            StoredGame(
                hostId = hostId,
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = false,
            )
        every { gameRepository.loadGame(gameId = gameId) } returns originalStoredGame
        val expectedUpdateOperations: List<UpdateStoredGameOperation> = listOf(SetStartedOperation(started = true))
        val newStoredGame =
            StoredGame(
                hostId = hostId,
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = true,
            )
        every {
            gameRepository.updateGame(
                gameId = gameId,
                updateOperations = expectedUpdateOperations,
                ttl = 3600000,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        } returns newStoredGame

        val actualResult: Game = gameService.startGame(gameId = gameId, playerId = hostId)

        val expectedResult: Game =
            GameImpl(
                id = gameId,
                hostId = hostId,
                players = mapOf(
                    "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                    "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                ),
                started = true,
            )
        assertEquals(expectedResult, actualResult)
        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
            gameRepository.updateGame(
                gameId = gameId,
                updateOperations = expectedUpdateOperations,
                ttl = 3600000,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        }
        confirmVerified(gameIdGenerator, gameRepository)
    }

    @Test
    fun `the ttl when starting a game can be configured`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk(relaxed = true)
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMutableMap<String, String> = ObservableMutableMapImpl(ConcurrentHashMap())
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val hostId = "test_hostId"
        val originalStoredGame =
            StoredGame(
                hostId = hostId,
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = false,
            )
        every { gameRepository.loadGame(gameId = gameId) } returns originalStoredGame
        val expectedUpdateOperations: List<UpdateStoredGameOperation> = listOf(SetStartedOperation(started = true))
        val newStoredGame =
            StoredGame(
                hostId = hostId,
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = true,
            )
        val ttlMs: Long = 97846
        appConfig["game.ttl.ms"] = ttlMs.toString()
        every {
            gameRepository.updateGame(
                gameId = gameId,
                updateOperations = expectedUpdateOperations,
                ttl = ttlMs,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        } returns newStoredGame

        val actualResult: Game = gameService.startGame(gameId = gameId, playerId = hostId)

        val expectedResult: Game =
            GameImpl(
                id = gameId,
                hostId = hostId,
                players = mapOf(
                    "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                    "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                ),
                started = true,
            )
        assertEquals(expectedResult, actualResult)
        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
            gameRepository.updateGame(
                gameId = gameId,
                updateOperations = expectedUpdateOperations,
                ttl = ttlMs,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        }
        confirmVerified(gameIdGenerator, gameRepository)
    }

    @Test
    fun `when a game is started then an event is published`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = ObservableMutableMapImpl(ConcurrentHashMap())
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val hostId = "test_hostId"
        val originalStoredGame =
            StoredGame(
                hostId = hostId,
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = false,
            )
        every { gameRepository.loadGame(gameId = gameId) } returns originalStoredGame
        val expectedUpdateOperations: List<UpdateStoredGameOperation> = listOf(SetStartedOperation(started = true))
        val newStoredGame =
            StoredGame(
                hostId = hostId,
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = true,
            )
        every {
            gameRepository.updateGame(
                gameId = gameId,
                updateOperations = expectedUpdateOperations,
                ttl = 3600000,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
        } returns newStoredGame
        val expectedEvent: GameEventEventApiV1Model =
            GameStartedEventEventApiV1Model(
                gameId = gameId,
                game = GameEventApiV1Model(
                    hostId = hostId,
                    players = mapOf(
                        "test_playerId1" to PlayerEventApiV1Model(name = "test_playerName1"),
                        "test_playerId2" to PlayerEventApiV1Model(name = "test_playerName2"),
                    ),
                    started = true,
                ),
            )
        justRun { eventApiService.publishGameEvent(event = expectedEvent) }

        gameService.startGame(gameId = gameId, playerId = hostId)

        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
            gameRepository.updateGame(
                gameId = gameId,
                updateOperations = expectedUpdateOperations,
                ttl = 3600000,
                ttlUnit = TimeUnit.MILLISECONDS,
            )
            eventApiService.publishGameEvent(event = expectedEvent)
        }
        confirmVerified(gameIdGenerator, gameRepository, eventApiService)
    }

    @Test
    fun `when a game that does not exist is started then an exception is thrown and no operation is performed`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk(relaxed = true)
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = mockk()
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val hostId = "test_hostId"
        every { gameRepository.loadGame(gameId = gameId) } returns null

        val exception: NoSuchGameFoundException =
            assertThrows { gameService.startGame(gameId = gameId, playerId = hostId) }

        assertEquals(gameId, exception.gameId)
        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
        }
        confirmVerified(gameIdGenerator, gameRepository, eventApiService)
    }

    @Test
    fun `when a game is started but the player id is not the host id then an exception is thrown and no operation is performed`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk(relaxed = true)
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = mockk()
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val hostId = "test_hostId"
        val otherPlayerId = "test_playerId1"
        val originalStoredGame =
            StoredGame(
                hostId = hostId,
                players = mapOf(
                    otherPlayerId to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = false,
            )
        every { gameRepository.loadGame(gameId = gameId) } returns originalStoredGame

        val exception: UserIsNotHostException =
            assertThrows { gameService.startGame(gameId = gameId, playerId = otherPlayerId) }

        assertEquals(gameId, exception.gameId)
        assertEquals(otherPlayerId, exception.playerId)
        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
        }
        confirmVerified(gameIdGenerator, gameRepository, eventApiService)
    }

    @Test
    fun `when a game that has already started is started then an exception is thrown and no operation is performed`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk(relaxed = true)
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = mockk()
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val hostId = "test_hostId"
        val originalStoredGame =
            StoredGame(
                hostId = hostId,
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = true,
            )
        every { gameRepository.loadGame(gameId = gameId) } returns originalStoredGame

        val exception: GameAlreadyStartedException =
            assertThrows { gameService.startGame(gameId = gameId, playerId = hostId) }

        assertEquals(gameId, exception.gameId)
        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
        }
        confirmVerified(gameIdGenerator, gameRepository, eventApiService)
    }

    @Test
    fun `an existing game can be ended`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk(relaxed = true)
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = mockk()
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val hostId = "test_hostId"
        val originalStoredGame =
            StoredGame(
                hostId = hostId,
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = true,
            )
        every { gameRepository.loadGame(gameId = gameId) } returns originalStoredGame
        every { gameRepository.deleteGame(gameId = gameId) } returns originalStoredGame

        gameService.endGame(gameId = gameId, playerId = hostId)

        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
            gameRepository.deleteGame(gameId = gameId)
        }
        confirmVerified(gameIdGenerator, gameRepository)
    }

    @Test
    fun `when a game is ended then an event is published`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = mockk()
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val hostId = "test_hostId"
        val originalStoredGame =
            StoredGame(
                hostId = hostId,
                players = mapOf(
                    "test_playerId1" to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = true,
            )
        every { gameRepository.loadGame(gameId = gameId) } returns originalStoredGame
        every { gameRepository.deleteGame(gameId = gameId) } returns originalStoredGame
        val expectedEvent: GameEventEventApiV1Model = GameEndedEventEventApiV1Model(gameId = gameId)
        justRun { eventApiService.publishGameEvent(event = expectedEvent) }

        gameService.endGame(gameId = gameId, playerId = hostId)

        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
            gameRepository.deleteGame(gameId = gameId)
            eventApiService.publishGameEvent(event = expectedEvent)
        }
        confirmVerified(gameIdGenerator, gameRepository, eventApiService)
    }

    @Test
    fun `when a game that does not exist is ended then an exception is thrown and no operation is performed`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk(relaxed = true)
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = mockk()
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val hostId = "test_hostId"
        every { gameRepository.loadGame(gameId = gameId) } returns null

        val exception: NoSuchGameFoundException =
            assertThrows { gameService.endGame(gameId = gameId, playerId = hostId) }

        assertEquals(gameId, exception.gameId)
        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
        }
        confirmVerified(gameIdGenerator, gameRepository, eventApiService)
    }

    @Test
    fun `when a game is ended but the player id is not the host id then an exception is thrown and no operation is performed`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk(relaxed = true)
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = mockk()
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val gameId = "test_gameId"
        val hostId = "test_hostId"
        val otherPlayerId = "test_playerId1"
        val originalStoredGame =
            StoredGame(
                hostId = hostId,
                players = mapOf(
                    otherPlayerId to StoredPlayer(name = "test_playerName1"),
                    "test_playerId2" to StoredPlayer(name = "test_playerName2"),
                ),
                started = false,
            )
        every { gameRepository.loadGame(gameId = gameId) } returns originalStoredGame

        val exception: UserIsNotHostException =
            assertThrows { gameService.endGame(gameId = gameId, playerId = otherPlayerId) }

        assertEquals(gameId, exception.gameId)
        assertEquals(otherPlayerId, exception.playerId)
        verify(ordering = Ordering.SEQUENCE) {
            gameRepository.loadGame(gameId = gameId)
        }
        confirmVerified(gameIdGenerator, gameRepository, eventApiService)
    }

    @Test
    @Suppress("ControlFlowWithEmptyBody")
    fun `game event listeners can be registered and unregistered`() {
        val gameRepository: GameRepository = mockk()
        val gameFactory = GameFactoryImpl()
        val playerFactory = PlayerFactoryImpl()
        val gameEventFactory = GameEventFactoryImpl()
        val eventApiService: EventApiV1Service = mockk()
        val sharedDataService: SharedDataService =
            SimpleClusterManager.createSharedDataService(clusterId = "test_clusterId")
        val gameIdGenerator: GameIdGenerator = mockk()
        val modelMapper =
            ModelMapperImpl(
                gameFactory = gameFactory,
                gameEventFactory = gameEventFactory,
                playerFactory = playerFactory,
            )
        val appConfig: ObservableMap<String, String> = mockk()
        val gameService =
            GameServiceImpl(
                gameRepository = gameRepository,
                gameFactory = gameFactory,
                playerFactory = playerFactory,
                gameEventFactory = gameEventFactory,
                eventApiService = eventApiService,
                sharedDataService = sharedDataService,
                gameIdGenerator = gameIdGenerator,
                modelMapper = modelMapper,
                appConfig = appConfig,
            )

        val eventHandlerSlot: CapturingSlot<GameEventEventApiV1Handler> = slot()
        justRun { eventApiService.subscribeToGameEvents(capture(eventHandlerSlot)) }

        gameService.register().get(2, TimeUnit.SECONDS)

        val gameId = "test_gameId"
        val listener1Events: MutableSet<GameEvent> = ConcurrentHashMap.newKeySet()
        val listener1Id: String = gameService.addGameListener(gameId) { listener1Events.add(it) }
        val listener2Events: MutableSet<GameEvent> = ConcurrentHashMap.newKeySet()
        val listener2Id: String = gameService.addGameListener(gameId) { listener2Events.add(it) }

        val eventApiEvent1: GameEventEventApiV1Model =
            PlayerAddedEventEventApiV1Model(
                gameId = gameId,
                game = GameEventApiV1Model(
                    hostId = "test_hostId",
                    players = mapOf(
                        "test_playerId1" to PlayerEventApiV1Model(name = "test_playerName1"),
                        "test_playerId2" to PlayerEventApiV1Model(name = "test_playerName2"),
                    ),
                    started = false,
                ),
            )
        eventHandlerSlot.captured.handle(event = eventApiEvent1)

        val expectedListener1Events1: Set<GameEvent> =
            setOf(
                PlayerAddedEventImpl(
                    game = GameImpl(
                        id = gameId,
                        hostId = "test_hostId",
                        players = mapOf(
                            "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                            "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                        ),
                        started = false,
                    ),
                ),
            )
        CompletableFuture.runAsync { while (listener1Events.size != expectedListener1Events1.size); }
            .get(2, TimeUnit.SECONDS)
        assertEquals(expectedListener1Events1, listener1Events)
        val expectedListener2Events1: Set<GameEvent> =
            setOf(
                PlayerAddedEventImpl(
                    game = GameImpl(
                        id = gameId,
                        hostId = "test_hostId",
                        players = mapOf(
                            "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                            "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                        ),
                        started = false,
                    ),
                ),
            )
        CompletableFuture.runAsync { while (listener2Events.size != expectedListener2Events1.size); }
            .get(2, TimeUnit.SECONDS)
        assertEquals(expectedListener2Events1, listener2Events)

        gameService.removeGameListener(gameId, listener1Id)

        val eventApiEvent2: GameEventEventApiV1Model =
            GameStartedEventEventApiV1Model(
                gameId = gameId,
                game = GameEventApiV1Model(
                    hostId = "test_hostId",
                    players = mapOf(
                        "test_playerId1" to PlayerEventApiV1Model(name = "test_playerName1"),
                        "test_playerId2" to PlayerEventApiV1Model(name = "test_playerName2"),
                    ),
                    started = true,
                ),
            )
        eventHandlerSlot.captured.handle(event = eventApiEvent2)

        val expectedListener1Events2: Set<GameEvent> =
            setOf(
                PlayerAddedEventImpl(
                    game = GameImpl(
                        id = gameId,
                        hostId = "test_hostId",
                        players = mapOf(
                            "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                            "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                        ),
                        started = false,
                    ),
                ),
            )
        CompletableFuture.runAsync { while (listener1Events.size != expectedListener1Events2.size); }
            .get(2, TimeUnit.SECONDS)
        assertEquals(expectedListener1Events2, listener1Events)
        val expectedListener2Events2: Set<GameEvent> =
            setOf(
                PlayerAddedEventImpl(
                    game = GameImpl(
                        id = gameId,
                        hostId = "test_hostId",
                        players = mapOf(
                            "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                            "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                        ),
                        started = false,
                    ),
                ),
                GameStartedEventImpl(
                    game = GameImpl(
                        id = gameId,
                        hostId = "test_hostId",
                        players = mapOf(
                            "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                            "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                        ),
                        started = true,
                    ),
                ),
            )
        CompletableFuture.runAsync { while (listener2Events.size != expectedListener2Events2.size); }
            .get(2, TimeUnit.SECONDS)
        assertEquals(expectedListener2Events2, listener2Events)

        gameService.removeGameListener(gameId, listener2Id)

        val eventApiEvent3: GameEventEventApiV1Model = GameEndedEventEventApiV1Model(gameId = gameId)
        eventHandlerSlot.captured.handle(event = eventApiEvent3)

        val expectedListener1Events3: Set<GameEvent> =
            setOf(
                PlayerAddedEventImpl(
                    game = GameImpl(
                        id = gameId,
                        hostId = "test_hostId",
                        players = mapOf(
                            "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                            "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                        ),
                        started = false,
                    ),
                ),
            )
        CompletableFuture.runAsync { while (listener1Events.size != expectedListener1Events3.size); }
            .get(2, TimeUnit.SECONDS)
        assertEquals(expectedListener1Events3, listener1Events)
        val expectedListener2Events3: Set<GameEvent> =
            setOf(
                PlayerAddedEventImpl(
                    game = GameImpl(
                        id = gameId,
                        hostId = "test_hostId",
                        players = mapOf(
                            "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                            "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                        ),
                        started = false,
                    ),
                ),
                GameStartedEventImpl(
                    game = GameImpl(
                        id = gameId,
                        hostId = "test_hostId",
                        players = mapOf(
                            "test_playerId1" to PlayerImpl(name = "test_playerName1"),
                            "test_playerId2" to PlayerImpl(name = "test_playerName2"),
                        ),
                        started = true,
                    ),
                ),
            )
        CompletableFuture.runAsync { while (listener2Events.size != expectedListener2Events3.size); }
            .get(2, TimeUnit.SECONDS)
        assertEquals(expectedListener2Events3, listener2Events)

        confirmVerified(gameIdGenerator, gameRepository)
    }
}
