package uk.co.rafearnold.captainsonar

import com.privacylogistics.FF3Cipher
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FpeGameIdGenerator(
    private val plainTextGameIdGenerator: GameIdGenerator,
    private val cipherProvider: FpeGameIdCipherProvider,
) : GameIdGenerator {

    private val cipher: FF3Cipher get() = cipherProvider.get()

    override fun generateId(): String {
        val plainTextId: String = plainTextGameIdGenerator.generateId()
        val encryptedId: String = cipher.encrypt(plainTextId)
        log.debug("Plain text game ID '$plainTextId' encrypted to '$encryptedId'")
        return encryptedId
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(FpeGameIdGenerator::class.java)
    }
}
