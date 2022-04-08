package uk.co.rafearnold.captainsonar.repository

import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.Jedis
import uk.co.rafearnold.captainsonar.config.ObservableMap
import uk.co.rafearnold.captainsonar.config.addListener
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RedisClientProvider @Inject constructor(
    private val appConfig: ObservableMap<String, String>
) : Provider<Jedis> {

    private var client: Jedis = buildClient()

    init {
        appConfig.addListener(keyRegex = "\\Qredis.connection.\\E(?:host|port|password)") { client = buildClient() }
    }

    override fun get(): Jedis = client

    @Synchronized
    private fun buildClient(): Jedis {
        val host: String = appConfig.getValue("redis.connection.host")
        val port: Int = appConfig.getValue("redis.connection.port").toInt()
        val password: String? = appConfig["redis.connection.password"]
        val configBuilder: DefaultJedisClientConfig.Builder = DefaultJedisClientConfig.builder()
        if (password != null) configBuilder.password(password)
        return Jedis(host, port, configBuilder.build())
    }
}
