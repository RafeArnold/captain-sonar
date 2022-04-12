package uk.co.rafearnold.captainsonar.repository.redis

import redis.clients.jedis.Jedis
import uk.co.rafearnold.captainsonar.repository.GameIdRepository
import javax.inject.Inject

class RedisGameIdRepository @Inject constructor(
    private val redisClientProvider: RedisClientProvider,
) : GameIdRepository {

    private val redisClient: Jedis get() = redisClientProvider.get()

    override fun getAndIncrementIdIndex(): Int =
        Math.floorMod(redisClient.use { it.incr(indexKey) } - 1, Int.MAX_VALUE.toLong()).toInt()

    companion object {
        private const val indexKey = "uk.co.rafearnold.captainsonar.game.id-generator.index"
    }
}
