package uk.co.rafearnold.captainsonar.repository.redis

import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl

interface RedisSessionCodec {

    fun serialize(session: SharedDataSessionImpl): ByteArray

    fun deserialize(bytes: ByteArray): SharedDataSessionImpl
}
