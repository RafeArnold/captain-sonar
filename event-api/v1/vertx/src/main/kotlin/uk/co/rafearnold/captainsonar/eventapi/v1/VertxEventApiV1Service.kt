package uk.co.rafearnold.captainsonar.eventapi.v1

import io.vertx.core.Vertx
import io.vertx.core.eventbus.DeliveryOptions
import uk.co.rafearnold.captainsonar.eventapi.v1.model.GameEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapi.v1.model.codec.VertxGameEventEventApiV1ModelCodec
import javax.inject.Inject

class VertxEventApiV1Service @Inject constructor(
    private val vertx: Vertx
) : EventApiV1Service {

    override fun publishGameEvent(event: GameEventEventApiV1Model) {
        val deliveryOptions: DeliveryOptions =
            DeliveryOptions().setCodecName(VertxGameEventEventApiV1ModelCodec.name)
        vertx.eventBus().publish(gameEventAddress, event, deliveryOptions)
    }

    override fun subscribeToGameEvents(handler: GameEventEventApiV1Handler) {
        vertx.eventBus().consumer<GameEventEventApiV1Model>(gameEventAddress) { handler.handle(it.body()) }
    }

    companion object {
        private const val gameEventAddress =
            "uk.co.rafearnold.captainsonar.event-api.v1.vertx.topic-address.game-event"
    }
}
