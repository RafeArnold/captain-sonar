package uk.co.rafearnold.captainsonar.event.model.codec

import com.fasterxml.jackson.databind.ObjectMapper
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec
import io.vertx.core.eventbus.impl.codecs.StringMessageCodec
import uk.co.rafearnold.captainsonar.common.Register
import uk.co.rafearnold.captainsonar.event.model.GameEventEventApiV1Model
import java.util.concurrent.CompletableFuture
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GameEventEventApiV1ModelCodec @Inject constructor(
    private val objectMapper: ObjectMapper,
    private val vertx: Vertx
) : MessageCodec<GameEventEventApiV1Model, GameEventEventApiV1Model>, Register {

    private val stringMessageCodec = StringMessageCodec()

    override fun encodeToWire(buffer: Buffer, s: GameEventEventApiV1Model) {
        stringMessageCodec.encodeToWire(buffer, objectMapper.writeValueAsString(s))
    }

    override fun decodeFromWire(pos: Int, buffer: Buffer): GameEventEventApiV1Model =
        objectMapper.readValue(stringMessageCodec.decodeFromWire(pos, buffer), GameEventEventApiV1Model::class.java)

    override fun transform(s: GameEventEventApiV1Model): GameEventEventApiV1Model = s

    override fun systemCodecID(): Byte = -1

    override fun name(): String = name

    override fun register(): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            vertx.eventBus().registerDefaultCodec(GameEventEventApiV1Model::class.java, this)
        }

    companion object {
        val name: String = GameEventEventApiV1ModelCodec::class.java.name
    }
}
