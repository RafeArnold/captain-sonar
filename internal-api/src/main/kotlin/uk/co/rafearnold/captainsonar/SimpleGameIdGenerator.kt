package uk.co.rafearnold.captainsonar

import com.google.common.math.IntMath
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.co.rafearnold.captainsonar.repository.GameIdRepository

class SimpleGameIdGenerator(
    gameIdAlphabet: String,
    private val idLength: Int,
    private val gameIdRepository: GameIdRepository,
) : GameIdGenerator {

    private val charArray: CharArray = gameIdAlphabet.toCharArray()

    override fun generateId(): String = generateIdFromIndex(index = gameIdRepository.getAndIncrementIdIndex())

    private fun generateIdFromIndex(index: Int): String {
        val plainTextIdBuilder: StringBuilder = StringBuilder(idLength)
        for (charIndex: Int in 0 until idLength) {
            val charArrayIndex: Int = index / IntMath.pow(charArray.size, charIndex) % charArray.size
            plainTextIdBuilder.append(charArray[charArrayIndex])
        }
        val plainTextId: String = plainTextIdBuilder.toString()
        log.debug("Game ID '$plainTextId' generated from index $index")
        return plainTextId
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(SimpleGameIdGenerator::class.java)
    }
}
