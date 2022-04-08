package uk.co.rafearnold.captainsonar.repository

import com.google.inject.AbstractModule
import com.google.inject.Scopes

class SharedDataRepositoryModule : AbstractModule() {

    override fun configure() {
        bind(GameRepository::class.java).to(SharedDataGameRepository::class.java).`in`(Scopes.SINGLETON)
    }
}
