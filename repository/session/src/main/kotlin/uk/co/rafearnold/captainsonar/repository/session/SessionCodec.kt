package uk.co.rafearnold.captainsonar.repository.session

import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl

interface SessionCodec {

    fun serialize(session: SharedDataSessionImpl): ByteArray

    fun deserialize(bytes: ByteArray): SharedDataSessionImpl
}
