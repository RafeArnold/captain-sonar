package uk.co.rafearnold.captainsonar

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.co.rafearnold.captainsonar.repository.GameIdRepository
import uk.co.rafearnold.commons.config.ObservableMap
import uk.co.rafearnold.commons.config.ObservableMutableMap
import uk.co.rafearnold.commons.config.ObservableMutableMapImpl
import uk.co.rafearnold.commons.config.addListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class InMemoryRandomGameIdGeneratorTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `id are generated as expected`() {
        val gameIdRepository: GameIdRepository = mockk()
        val randomSeed = "8066363044057040708"
        val appConfig: ObservableMutableMap<String, String> =
            ObservableMutableMapImpl(backingMap = ConcurrentHashMap())
        appConfig["game.id-generator.in-memory-random.random-seed"] = randomSeed
        val generator = InMemoryRandomGameIdGenerator(gameIdRepository = gameIdRepository, appConfig = appConfig)

        every { gameIdRepository.getAndIncrementIdIndex() } returns 0
        assertEquals("SYGR", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 1
        assertEquals("GXC0", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 9
        assertEquals("EDAK", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 10
        assertEquals("FDR9", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 35
        assertEquals("EU3Q", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 36
        assertEquals("JDFC", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 37
        assertEquals("5NQS", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 71
        assertEquals("HAIQ", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 1679614
        assertEquals("IQFM", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 1679615
        assertEquals("Y7WQ", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 1679616
        assertEquals("SYGR", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 1679617
        assertEquals("GXC0", generator.generateId())
    }

    @Test
    fun `random seed can be updated`() {
        val gameIdRepository: GameIdRepository = mockk()
        val appConfig: ObservableMutableMap<String, String> =
            spyk(ObservableMutableMapImpl(backingMap = ConcurrentHashMap()))

        val randomSeed1 = "8066363044057040708"
        appConfig["game.id-generator.in-memory-random.random-seed"] = randomSeed1

        val appConfigFuture: CompletableFuture<Void> = CompletableFuture()
        mockkStatic("uk.co.rafearnold.captainsonar.config.ObservableMapExtensionFunctionsKt")
        every {
            appConfig.addListener(keyRegex = any(), listener = any())
        } answers {
            val originalListener: ObservableMap.Listener<String, String> = thirdArg()
            val wrappingListener: ObservableMap.Listener<String, String> =
                ObservableMap.Listener {
                    try {
                        originalListener.handle(it)
                    } finally {
                        appConfigFuture.complete(null)
                    }
                }
            val regex = Regex(secondArg())
            appConfig.addListener({ regex.matches(it) }, wrappingListener)
        }

        val generator = InMemoryRandomGameIdGenerator(gameIdRepository = gameIdRepository, appConfig = appConfig)

        every { gameIdRepository.getAndIncrementIdIndex() } returns 0
        assertEquals("SYGR", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 1
        assertEquals("GXC0", generator.generateId())

        val randomSeed2 = "9052670971019418917"
        appConfig["game.id-generator.in-memory-random.random-seed"] = randomSeed2

        appConfigFuture.get(2, TimeUnit.SECONDS)

        every { gameIdRepository.getAndIncrementIdIndex() } returns 0
        assertEquals("E9V3", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 1
        assertEquals("KTZ7", generator.generateId())
    }
}
