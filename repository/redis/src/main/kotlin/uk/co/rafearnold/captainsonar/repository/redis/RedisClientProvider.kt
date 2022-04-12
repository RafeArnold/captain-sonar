package uk.co.rafearnold.captainsonar.repository.redis

import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import uk.co.rafearnold.captainsonar.config.ObservableMap
import uk.co.rafearnold.captainsonar.config.addListener
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RedisClientProvider @Inject constructor(
    private val appConfig: ObservableMap<String, String>
) : Provider<Jedis> {

    private var pool: JedisPool = buildPool()

    init {
        appConfig.addListener(keyRegex = "\\Qredis.connection.\\E(?:host|port|password|pool-size)") {
            pool = buildPool()
        }
    }

    override fun get(): Jedis = pool.resource

    @Synchronized
    private fun buildPool(): JedisPool {
        val host: String = appConfig.getValue("redis.connection.host")
        val port: Int = appConfig.getValue("redis.connection.port").toInt()
        val password: String? = appConfig["redis.connection.password"]
        val configBuilder: DefaultJedisClientConfig.Builder = DefaultJedisClientConfig.builder()
        if (password != null) configBuilder.password(password)
        val poolConfig = JedisPoolConfig()
        val maxPoolSize: Int? = appConfig["redis.connection.pool-size"]?.toInt()
        if (maxPoolSize != null) poolConfig.maxTotal = maxPoolSize
        return JedisPool(poolConfig, HostAndPort(host, port), configBuilder.build())
    }
}
