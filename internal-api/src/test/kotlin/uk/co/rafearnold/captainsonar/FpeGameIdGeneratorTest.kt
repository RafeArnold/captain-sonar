package uk.co.rafearnold.captainsonar

import com.privacylogistics.FF3Cipher
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class FpeGameIdGeneratorTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `plain text ids are encrypted as expected`() {
        val plainTextGameIdGenerator: GameIdGenerator = mockk()
        val cipherProvider: FpeGameIdCipherProvider = mockk()
        val generator =
            FpeGameIdGenerator(plainTextGameIdGenerator = plainTextGameIdGenerator, cipherProvider = cipherProvider)

        val key = "2DE79D232DF5585D68CE47882AE256D6"
        val tweak = "CBD09280979564"
        val cipher = FF3Cipher(key, tweak, 36)
        every { cipherProvider.get() } returns cipher

        every { plainTextGameIdGenerator.generateId() } returns "000000"
        assertEquals("KCB2W6", generator.generateId())

        every { plainTextGameIdGenerator.generateId() } returns "100000"
        assertEquals("ECT68C", generator.generateId())

        every { plainTextGameIdGenerator.generateId() } returns "900000"
        assertEquals("VEBG8J", generator.generateId())

        every { plainTextGameIdGenerator.generateId() } returns "A00000"
        assertEquals("G4VBDP", generator.generateId())

        every { plainTextGameIdGenerator.generateId() } returns "Z00000"
        assertEquals("DBWJ3E", generator.generateId())

        every { plainTextGameIdGenerator.generateId() } returns "010000"
        assertEquals("3RB442", generator.generateId())

        every { plainTextGameIdGenerator.generateId() } returns "110000"
        assertEquals("5SKESK", generator.generateId())

        every { plainTextGameIdGenerator.generateId() } returns "Z10000"
        assertEquals("VI21SM", generator.generateId())

        every { plainTextGameIdGenerator.generateId() } returns "YZZZZZ"
        assertEquals("4SACCV", generator.generateId())

        every { plainTextGameIdGenerator.generateId() } returns "ZZZZZZ"
        assertEquals("1JAEBM", generator.generateId())
    }

    @Test
    fun `encrypted ids are always the same number of characters as the generated plain text id`() {
        val plainTextGameIdGenerator: GameIdGenerator = mockk()
        val cipherProvider: FpeGameIdCipherProvider = mockk()
        val generator =
            FpeGameIdGenerator(plainTextGameIdGenerator = plainTextGameIdGenerator, cipherProvider = cipherProvider)

        val key = "2DE79D232DF5585D68CE47882AE256D6"
        val tweak = "CBD09280979564"
        val cipher = FF3Cipher(key, tweak, 36)
        every { cipherProvider.get() } returns cipher

        for (i in 1..100) {
            val plainTextId: String =
                (1..Random.nextInt(6, 20))
                    .fold(StringBuilder()) { acc, _ -> acc.append(charArray[Random.nextInt(0, charArray.size)]) }
                    .toString()
            every { plainTextGameIdGenerator.generateId() } returns plainTextId
            assertEquals(plainTextId.length, generator.generateId().length)
        }
    }

    companion object {
        private val charArray: CharArray =
            charArrayOf(
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
                'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
            )
    }
}
