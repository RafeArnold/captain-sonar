package uk.co.rafearnold.captainsonar.eventapiv1

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.eventapiv1.model.codec.GameEventEventApiV1ModelCodec

class EventApiV1Module : AbstractModule() {

    override fun configure() {
        bind(EventApiV1Service::class.java).to(EventApiV1ServiceImpl::class.java).`in`(Scopes.SINGLETON)
        bindRegisters()
    }

    private fun bindRegisters() {
        val multibinder: Multibinder<Register> = Multibinder.newSetBinder(binder(), Register::class.java)
        multibinder.addBinding().to(GameEventEventApiV1ModelCodec::class.java).`in`(Scopes.SINGLETON)
    }
}
