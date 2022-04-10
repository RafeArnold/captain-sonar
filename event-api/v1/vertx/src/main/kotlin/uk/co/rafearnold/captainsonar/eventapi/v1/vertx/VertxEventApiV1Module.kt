package uk.co.rafearnold.captainsonar.eventapi.v1.vertx

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.eventapi.v1.EventApiV1Service
import uk.co.rafearnold.captainsonar.eventapi.v1.vertx.model.codec.VertxGameEventEventApiV1ModelCodec

class VertxEventApiV1Module : AbstractModule() {

    override fun configure() {
        bind(EventApiV1Service::class.java).to(VertxEventApiV1Service::class.java).`in`(Scopes.SINGLETON)
        bindRegisters()
    }

    private fun bindRegisters() {
        val multibinder: Multibinder<Register> = Multibinder.newSetBinder(binder(), Register::class.java)
        multibinder.addBinding().to(VertxGameEventEventApiV1ModelCodec::class.java).`in`(Scopes.SINGLETON)
    }
}
