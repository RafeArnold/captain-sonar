package uk.co.rafearnold.captainsonar

import com.google.common.math.IntMath
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.config.ObservableMap
import uk.co.rafearnold.captainsonar.config.addListener
import uk.co.rafearnold.captainsonar.repository.GameIdRepository
import java.security.SecureRandom
import javax.inject.Inject

class InMemoryRandomGameIdGenerator @Inject constructor(
    private val gameIdRepository: GameIdRepository,
    private val appConfig: ObservableMap<String, String>,
) : GameIdGenerator {

    private var shuffledIndexArray: IntArray = buildShuffledIndexArray()

    init {
        appConfig.addListener(keyRegex = "\\Q$randomSeedAppPropName\\E") {
            shuffledIndexArray = buildShuffledIndexArray()
        }
    }

    private fun buildShuffledIndexArray(): IntArray {
        val random: SecureRandom = SecureRandom.getInstance("SHA1PRNG")
        random.setSeed(appConfig.getValue(randomSeedAppPropName).toLong())
        return (0 until totalPermutations)
            .shuffled(random)
            .toIntArray()
    }

    override fun generateId(): String = generateIdFromIndex(index = gameIdRepository.getAndIncrementIdIndex())

    private fun generateIdFromIndex(index: Int): String {
        val shuffledIndex: Int = shuffledIndexArray[index % totalPermutations]
        val idBuilder: StringBuilder = StringBuilder(idLength)
        for (charIndex: Int in 0 until idLength) {
            idBuilder.append(charArray[shuffledIndex / IntMath.pow(charArray.size, charIndex) % charArray.size])
        }
        val id: String = idBuilder.toString()
        log.debug("Game ID '$id' generated from index $index")
        return id
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InMemoryRandomGameIdGenerator::class.java)

        private const val idLength: Int = 4
        private val charArray: CharArray =
            charArrayOf(
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
                'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
            )
        private val totalPermutations: Int = IntMath.pow(charArray.size, idLength)

        private const val randomSeedAppPropName = "game.id-generator.in-memory-random.random-seed"
    }
}
