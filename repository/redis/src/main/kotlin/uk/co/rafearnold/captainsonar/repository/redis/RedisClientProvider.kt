package uk.co.rafearnold.captainsonar.repository.redis

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import uk.co.rafearnold.captainsonar.config.ObservableMap
import uk.co.rafearnold.captainsonar.config.addListener
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RedisClientProvider @Inject constructor(
    private val appConfig: ObservableMap<String, String>
) : Provider<Jedis> {

    private var pool: JedisPool = buildPool()

    private val configChangeHandlers: MutableCollection<ConfigChangeHandler> = mutableListOf()
    private val configChangeHandlersExecutor: Executor = Executors.newCachedThreadPool()

    init {
        appConfig.addListener(keyRegex = "\\Qredis.connection.\\E(?:host|port|password|pool-size)") {
            pool = buildPool()
            for (handler: ConfigChangeHandler in configChangeHandlers) {
                configChangeHandlersExecutor.execute {
                    try {
                        handler.handle()
                    } catch (e: Throwable) {
                        log.error("Subscription failed to handle config change event", e)
                    }
                }
            }
        }
    }

    override fun get(): Jedis = pool.resource

    fun subscribeToClientConfigChangeEvents(handler: ConfigChangeHandler) {
        configChangeHandlers.add(handler)
    }

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

    fun interface ConfigChangeHandler {
        fun handle()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RedisClientProvider::class.java)
    }
}
