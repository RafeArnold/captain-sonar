package uk.co.rafearnold.captainsonar

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.co.rafearnold.captainsonar.repository.GameIdRepository

class SimpleGameIdGeneratorTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `id are generated as expected`() {
        val gameIdAlphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val gameIdRepository: GameIdRepository = mockk()
        val generator =
            SimpleGameIdGenerator(gameIdAlphabet = gameIdAlphabet, idLength = 4, gameIdRepository = gameIdRepository)

        every { gameIdRepository.getAndIncrementIdIndex() } returns 0
        assertEquals("0000", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 1
        assertEquals("1000", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 9
        assertEquals("9000", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 10
        assertEquals("A000", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 35
        assertEquals("Z000", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 36
        assertEquals("0100", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 37
        assertEquals("1100", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 71
        assertEquals("Z100", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 1679614
        assertEquals("YZZZ", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 1679615
        assertEquals("ZZZZ", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 1679616
        assertEquals("0000", generator.generateId())

        every { gameIdRepository.getAndIncrementIdIndex() } returns 1679617
        assertEquals("1000", generator.generateId())
    }
}
