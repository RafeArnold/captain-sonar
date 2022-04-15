package uk.co.rafearnold.captainsonar.model.factory

import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.GameEndedEvent
import uk.co.rafearnold.captainsonar.model.GameStartedEvent
import uk.co.rafearnold.captainsonar.model.PlayerAddedEvent

interface GameEventFactory {
    fun createPlayerAddedEvent(game: Game): PlayerAddedEvent
    fun createGameStartedEvent(game: Game): GameStartedEvent
    fun createGameEndedEvent(): GameEndedEvent
}
