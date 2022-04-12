package uk.co.rafearnold.captainsonar

import com.privacylogistics.FF3Cipher
import uk.co.rafearnold.captainsonar.config.ObservableMap
import uk.co.rafearnold.captainsonar.config.addListener
import javax.inject.Provider

class FpeGameIdCipherProvider(
    private val gameIdAlphabet: String,
    private val appConfig: ObservableMap<String, String>,
) : Provider<FF3Cipher> {

    private var cipher: FF3Cipher = buildCipher()

    init {
        appConfig.addListener(keyRegex = "\\Qgame.id-generator.cipher.\\E(?:key|tweak)") { cipher = buildCipher() }
    }

    override fun get(): FF3Cipher = cipher

    @Synchronized
    private fun buildCipher(): FF3Cipher =
        FF3Cipher(
            appConfig.getValue("game.id-generator.cipher.key"),
            appConfig.getValue("game.id-generator.cipher.tweak"),
            gameIdAlphabet
        )
}
