package uk.co.rafearnold.captainsonar.eventapi.v1

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import uk.co.rafearnold.captainsonar.common.Register

class RabbitmqEventApiV1Module : AbstractModule() {

    override fun configure() {
        bind(EventApiV1Service::class.java).to(RabbitmqEventApiV1Service::class.java).`in`(Scopes.SINGLETON)
        bind(RabbitmqGameEventEventApiV1ConsumerFactory::class.java)
            .to(RabbitmqGameEventEventApiV1ConsumerFactoryImpl::class.java).`in`(Scopes.SINGLETON)
        bind(Connection::class.java).toProvider(RabbitmqConnectionProvider::class.java).`in`(Scopes.SINGLETON)
        bind(Channel::class.java).toProvider(RabbitmqChannelProvider::class.java)
        bindRegisters()
    }

    private fun bindRegisters() {
        val multibinder: Multibinder<Register> = Multibinder.newSetBinder(binder(), Register::class.java)
        multibinder.addBinding().to(RabbitmqEventApiV1Service::class.java).`in`(Scopes.SINGLETON)
    }
}
