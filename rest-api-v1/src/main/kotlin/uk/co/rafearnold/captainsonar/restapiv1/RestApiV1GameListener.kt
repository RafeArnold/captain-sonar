package uk.co.rafearnold.captainsonar.restapiv1

import uk.co.rafearnold.captainsonar.restapiv1.model.GameEventRestApiV1Model

fun interface RestApiV1GameListener {
    fun handle(event: GameEventRestApiV1Model)
}
