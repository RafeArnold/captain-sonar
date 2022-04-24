package uk.co.rafearnold.captainsonar.repository.session

import com.google.inject.AbstractModule
import com.google.inject.Scopes

class SessionRepositoryModule : AbstractModule() {

    override fun configure() {
        bind(SessionCodec::class.java).to(SessionCodecImpl::class.java).`in`(Scopes.SINGLETON)
    }
}
