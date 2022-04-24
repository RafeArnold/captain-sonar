package uk.co.rafearnold.captainsonar.repository.session

import io.vertx.core.buffer.Buffer
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl

class SessionCodecImpl : SessionCodec {

    override fun serialize(session: SharedDataSessionImpl): ByteArray {
        val buffer: Buffer = Buffer.buffer()
        session.writeToBuffer(buffer)
        return buffer.bytes
    }

    override fun deserialize(bytes: ByteArray): SharedDataSessionImpl {
        val session = SharedDataSessionImpl()
        session.readFromBuffer(0, Buffer.buffer(bytes))
        return session
    }
}
