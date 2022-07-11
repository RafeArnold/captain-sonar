package uk.co.rafearnold.captainsonar

import com.privacylogistics.FF3Cipher
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uk.co.rafearnold.commons.config.ObservableMap
import uk.co.rafearnold.commons.config.ObservableMutableMap
import uk.co.rafearnold.commons.config.ObservableMutableMapImpl
import uk.co.rafearnold.commons.config.addListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class FpeGameIdCipherProviderTest {

    @BeforeEach
    @AfterEach
    fun reset() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `cipher is constructed with the configured key, tweak and alphabet`() {
        val gameIdAlphabet = "abcdefghijklmnopqrstuvwxyz"
        val appConfig: ObservableMutableMap<String, String> =
            ObservableMutableMapImpl(backingMap = ConcurrentHashMap())

        val key = "718385E6542534604419E83CE387A437"
        val tweak = "B6F35084FA90E1"
        appConfig["game.id-generator.cipher.key"] = key
        appConfig["game.id-generator.cipher.tweak"] = tweak

        val cipherProvider = FpeGameIdCipherProvider(gameIdAlphabet = gameIdAlphabet, appConfig = appConfig)

        val cipher: FF3Cipher = cipherProvider.get()

        assertEquals("ywowehycyd", cipher.encrypt("wfmwlrorcd"))
    }

    @Test
    @Suppress("ControlFlowWithEmptyBody")
    fun `key and tweak can be updated`() {
        val gameIdAlphabet = "abcdefghijklmnopqrstuvwxyz"
        val appConfig: ObservableMutableMap<String, String> =
            ObservableMutableMapImpl(backingMap = ConcurrentHashMap())

        val key1 = "718385E6542534604419E83CE387A437"
        val tweak1 = "B6F35084FA90E1"
        appConfig["game.id-generator.cipher.key"] = key1
        appConfig["game.id-generator.cipher.tweak"] = tweak1

        val listenerEvents: MutableSet<ObservableMap.ListenEvent<String, String>> = ConcurrentHashMap.newKeySet()
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
                        listenerEvents.add(it)
                    }
                }
            val regex = Regex(secondArg())
            appConfig.addListener({ regex.matches(it) }, wrappingListener)
        }

        val cipherProvider = FpeGameIdCipherProvider(gameIdAlphabet = gameIdAlphabet, appConfig = appConfig)

        val cipher1: FF3Cipher = cipherProvider.get()

        assertEquals("ywowehycyd", cipher1.encrypt("wfmwlrorcd"))

        val key2 = "DB602DFF22ED7E84C8D8C865A941A238"
        val tweak2 = "EBEFD63BCC2083"
        appConfig["game.id-generator.cipher.key"] = key2
        appConfig["game.id-generator.cipher.tweak"] = tweak2

        CompletableFuture.runAsync { while (listenerEvents.size != 2); }.get(10, TimeUnit.SECONDS)

        val cipher2: FF3Cipher = cipherProvider.get()

        assertEquals(
            "belcfahcwwytwrckieymthabgjjfkxtxauipmjja",
            cipher2.encrypt("kkuomenbzqvggfbteqdyanwpmhzdmoicekiihkrm")
        )
    }
}
