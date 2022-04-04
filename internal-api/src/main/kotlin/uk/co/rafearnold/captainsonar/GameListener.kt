package uk.co.rafearnold.captainsonar

import uk.co.rafearnold.captainsonar.model.GameEvent

fun interface GameListener {
    fun handle(event: GameEvent)
}
