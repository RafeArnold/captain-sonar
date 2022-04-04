package uk.co.rafearnold.captainsonar.guice

import com.google.inject.Injector
import io.vertx.core.Promise
import io.vertx.core.Verticle
import io.vertx.core.spi.VerticleFactory
import java.util.concurrent.Callable
import javax.inject.Inject

class GuiceVerticleFactory @Inject constructor(private val injector: Injector) : VerticleFactory {

    override fun prefix(): String = prefix

    override fun createVerticle(
        verticleName: String,
        classLoader: ClassLoader,
        promise: Promise<Callable<Verticle>>
    ) {
        promise.complete {
            injector.getInstance(classLoader.loadClass(VerticleFactory.removePrefix(verticleName))) as Verticle
        }
    }

    companion object {
        const val prefix = "uk.co.rafearnold.guice"
    }
}
