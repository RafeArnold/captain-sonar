package uk.co.rafearnold.captainsonar.eventapi.v1.model.codec

import com.fasterxml.jackson.databind.ObjectMapper
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEventEventApiV1Model
import javax.inject.Inject

internal class RabbitmqGameEventEventApiV1ModelCodec @Inject constructor(
    private val objectMapper: ObjectMapper,
) {

    fun encode(event: GameEventEventApiV1Model): ByteArray = objectMapper.writeValueAsBytes(event)

    fun decode(byteArray: ByteArray): GameEventEventApiV1Model =
        objectMapper.readValue(byteArray, GameEventEventApiV1Model::class.java)
}
