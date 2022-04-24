package uk.co.rafearnold.captainsonar.restapiv1

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.restapiv1.handler.CreateGameRestApiV1HandlerRouteRegister
import uk.co.rafearnold.captainsonar.restapiv1.handler.EndGameRestApiV1HandlerRouteRegister
import uk.co.rafearnold.captainsonar.restapiv1.handler.GetGameStateRestApiV1HandlerRouteRegister
import uk.co.rafearnold.captainsonar.restapiv1.handler.JoinGameRestApiV1HandlerRouteRegister
import uk.co.rafearnold.captainsonar.restapiv1.handler.StartGameRestApiV1HandlerRouteRegister
import uk.co.rafearnold.captainsonar.restapiv1.handler.StreamGameRestApiV1HandlerRouteRegister
import uk.co.rafearnold.captainsonar.restapiv1.model.mapper.RestApiV1ModelMapper
import uk.co.rafearnold.captainsonar.restapiv1.model.mapper.RestApiV1ModelMapperImpl

class RestApiV1Module : AbstractModule() {

    override fun configure() {
        bind(RestApiV1Service::class.java).to(RestApiV1ServiceImpl::class.java).`in`(Scopes.SINGLETON)
        bind(RestApiV1SessionService::class.java).to(RestApiV1SessionServiceImpl::class.java).`in`(Scopes.SINGLETON)
        bind(RestApiV1ModelMapper::class.java).to(RestApiV1ModelMapperImpl::class.java).`in`(Scopes.SINGLETON)
        bindRegisters()
    }

    private fun bindRegisters() {
        val multibinder: Multibinder<Register> = Multibinder.newSetBinder(binder(), Register::class.java)
        multibinder.addBinding().to(RestApiV1ExpiredSessionListener::class.java).`in`(Scopes.SINGLETON)
        multibinder.addBinding().to(CreateGameRestApiV1HandlerRouteRegister::class.java).`in`(Scopes.SINGLETON)
        multibinder.addBinding().to(JoinGameRestApiV1HandlerRouteRegister::class.java).`in`(Scopes.SINGLETON)
        multibinder.addBinding().to(StartGameRestApiV1HandlerRouteRegister::class.java).`in`(Scopes.SINGLETON)
        multibinder.addBinding().to(EndGameRestApiV1HandlerRouteRegister::class.java).`in`(Scopes.SINGLETON)
        multibinder.addBinding().to(StreamGameRestApiV1HandlerRouteRegister::class.java).`in`(Scopes.SINGLETON)
        multibinder.addBinding().to(GetGameStateRestApiV1HandlerRouteRegister::class.java).`in`(Scopes.SINGLETON)
    }
}
