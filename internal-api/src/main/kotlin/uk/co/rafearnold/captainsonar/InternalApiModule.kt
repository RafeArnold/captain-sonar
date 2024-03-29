package uk.co.rafearnold.captainsonar

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.model.factory.GameEventFactory
import uk.co.rafearnold.captainsonar.model.factory.GameEventFactoryImpl
import uk.co.rafearnold.captainsonar.model.factory.GameFactory
import uk.co.rafearnold.captainsonar.model.factory.GameFactoryImpl
import uk.co.rafearnold.captainsonar.model.factory.PlayerFactory
import uk.co.rafearnold.captainsonar.model.factory.PlayerFactoryImpl
import uk.co.rafearnold.captainsonar.model.mapper.ModelMapper
import uk.co.rafearnold.captainsonar.model.mapper.ModelMapperImpl

class InternalApiModule : AbstractModule() {

    override fun configure() {
        bind(GameService::class.java).to(GameServiceImpl::class.java).`in`(Scopes.SINGLETON)
        bind(GameFactory::class.java).to(GameFactoryImpl::class.java).`in`(Scopes.SINGLETON)
        bind(PlayerFactory::class.java).to(PlayerFactoryImpl::class.java).`in`(Scopes.SINGLETON)
        bind(GameEventFactory::class.java).to(GameEventFactoryImpl::class.java).`in`(Scopes.SINGLETON)
        bind(ModelMapper::class.java).to(ModelMapperImpl::class.java).`in`(Scopes.SINGLETON)
        bind(GameIdGenerator::class.java).to(InMemoryRandomGameIdGenerator::class.java).`in`(Scopes.SINGLETON)
        bindRegisters()
    }

    private fun bindRegisters() {
        val multibinder: Multibinder<Register> = Multibinder.newSetBinder(binder(), Register::class.java)
        multibinder.addBinding().to(GameServiceImpl::class.java).`in`(Scopes.SINGLETON)
    }
}
