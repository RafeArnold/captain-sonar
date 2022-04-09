package uk.co.rafearnold.captainsonar.eventapiv1

import io.vertx.core.Vertx
import io.vertx.core.eventbus.DeliveryOptions
import uk.co.rafearnold.captainsonar.eventapiv1.model.GameEventEventApiV1Model
import uk.co.rafearnold.captainsonar.eventapiv1.model.codec.GameEventEventApiV1ModelCodec
import javax.inject.Inject

class EventApiV1ServiceImpl @Inject constructor(
    private val vertx: Vertx
) : EventApiV1Service {

    override fun publishGameEvent(event: GameEventEventApiV1Model) {
        val deliveryOptions: DeliveryOptions =
            DeliveryOptions().setCodecName(GameEventEventApiV1ModelCodec.name)
        vertx.eventBus().publish(gameEventAddress, event, deliveryOptions)
    }

    override fun subscribeToGameEvents(handler: GameEventHandler) {
        vertx.eventBus().consumer<GameEventEventApiV1Model>(gameEventAddress) { handler.handle(it.body()) }
    }

    companion object {
        private const val gameEventAddress = "uk.co.rafearnold.captainsonar.event-api-v1.topic-address.game-event"
    }
}
