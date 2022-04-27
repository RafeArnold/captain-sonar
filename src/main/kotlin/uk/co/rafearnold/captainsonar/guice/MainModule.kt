package uk.co.rafearnold.captainsonar.guice

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import com.hazelcast.core.HazelcastInstance
import io.vertx.config.ConfigRetriever
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.config.ConfigObserver
import uk.co.rafearnold.captainsonar.config.ConfigProvider
import uk.co.rafearnold.captainsonar.config.ConfigRetrieverProvider
import uk.co.rafearnold.captainsonar.config.ObservableMap
import uk.co.rafearnold.captainsonar.http.BodyHandlerRouteRegister
import uk.co.rafearnold.captainsonar.http.FrontEndHandlerRouteRegister
import uk.co.rafearnold.captainsonar.http.HealthCheckHandlerRouteRegister
import uk.co.rafearnold.captainsonar.http.LoggingHandlerRouteRegister
import uk.co.rafearnold.captainsonar.http.RouterProvider
import uk.co.rafearnold.captainsonar.http.ServerRegister
import uk.co.rafearnold.captainsonar.http.SessionHandlerRouteRegister

class MainModule(
    private val vertx: Vertx,
    private val initialConfig: JsonObject
) : AbstractModule() {

    override fun configure() {
        bind(Vertx::class.java).toInstance(vertx)
        bind(JsonObject::class.java).toInstance(initialConfig)
        bind(ConfigRetriever::class.java).toProvider(ConfigRetrieverProvider::class.java).`in`(Scopes.SINGLETON)
        bind(typeLiteral<ObservableMap<String, String>>()).toProvider(ConfigProvider::class.java).`in`(Scopes.SINGLETON)
        bind(Router::class.java).toProvider(RouterProvider::class.java).`in`(Scopes.SINGLETON)
        bind(ObjectMapper::class.java).toProvider(ObjectMapperProvider::class.java)
        bind(HazelcastInstance::class.java).toProvider(HazelcastInstanceProvider::class.java).`in`(Scopes.SINGLETON)
        bindRegisters()
    }

    private fun bindRegisters() {
        val multibinder: Multibinder<Register> = Multibinder.newSetBinder(binder(), Register::class.java)

        // Make sure this register is bound first, to ensure the app config is loaded first.
        multibinder.addBinding().to(ConfigProvider::class.java).`in`(Scopes.SINGLETON)

        multibinder.addBinding().to(BodyHandlerRouteRegister::class.java).`in`(Scopes.SINGLETON)
        multibinder.addBinding().to(LoggingHandlerRouteRegister::class.java).`in`(Scopes.SINGLETON)
        multibinder.addBinding().to(SessionHandlerRouteRegister::class.java).`in`(Scopes.SINGLETON)
        multibinder.addBinding().to(HealthCheckHandlerRouteRegister::class.java).`in`(Scopes.SINGLETON)
        multibinder.addBinding().to(FrontEndHandlerRouteRegister::class.java).`in`(Scopes.SINGLETON)

        multibinder.addBinding().to(ServerRegister::class.java).`in`(Scopes.SINGLETON)

        multibinder.addBinding().to(ConfigObserver::class.java).`in`(Scopes.SINGLETON)
    }
}
