package uk.co.rafearnold.captainsonar

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.Singleton
import uk.co.rafearnold.captainsonar.config.ObservableMap
import uk.co.rafearnold.captainsonar.repository.GameIdRepository

class FpeGameIdGeneratorModule : AbstractModule() {

    @Provides
    @Singleton
    fun plainTextGameIdGenerator(gameIdRepository: GameIdRepository): SimpleGameIdGenerator =
        SimpleGameIdGenerator(
            gameIdAlphabet = gameIdAlphabet,
            idLength = gameIdLength,
            gameIdRepository = gameIdRepository
        )

    @Provides
    @Singleton
    fun cipherProvider(appConfig: ObservableMap<String, String>): FpeGameIdCipherProvider =
        FpeGameIdCipherProvider(gameIdAlphabet = gameIdAlphabet, appConfig = appConfig)

    @Provides
    @Singleton
    fun gameIdGenerator(
        plainTextGameIdGenerator: SimpleGameIdGenerator,
        cipherProvider: FpeGameIdCipherProvider
    ): GameIdGenerator =
        FpeGameIdGenerator(plainTextGameIdGenerator = plainTextGameIdGenerator, cipherProvider = cipherProvider)

    companion object {
        private const val gameIdLength: Int = 6
        private const val gameIdAlphabet: String = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    }
}
