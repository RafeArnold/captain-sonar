package uk.co.rafearnold.captainsonar.repository

import com.google.inject.AbstractModule
import com.google.inject.Scopes
import com.google.inject.multibindings.Multibinder
import redis.clients.jedis.Jedis
import uk.co.rafearnold.captainsonar.common.Register

class RepositoryModule : AbstractModule() {

    override fun configure() {
        bind(GameRepository::class.java).to(RedisGameRepository::class.java).`in`(Scopes.SINGLETON)
        bindRegisters()
    }

    private fun bindRegisters() {
        val multibinder: Multibinder<Register> = Multibinder.newSetBinder(binder(), Register::class.java)
        multibinder.addBinding().to(RedisClientProvider::class.java).`in`(Scopes.SINGLETON)
    }
}
