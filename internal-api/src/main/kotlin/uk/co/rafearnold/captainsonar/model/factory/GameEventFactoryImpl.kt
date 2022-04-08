package uk.co.rafearnold.captainsonar.model.factory

import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.GameDeletedEvent
import uk.co.rafearnold.captainsonar.model.GameDeletedEventImpl
import uk.co.rafearnold.captainsonar.model.GameStartedEvent
import uk.co.rafearnold.captainsonar.model.GameStartedEventImpl
import uk.co.rafearnold.captainsonar.model.PlayerAddedEvent
import uk.co.rafearnold.captainsonar.model.PlayerAddedEventImpl

class GameEventFactoryImpl : GameEventFactory {

    override fun createPlayerAddedEvent(game: Game): PlayerAddedEvent = PlayerAddedEventImpl(game = game)

    override fun createGameStartedEvent(game: Game): GameStartedEvent = GameStartedEventImpl(game = game)

    override fun createGameDeletedEvent(): GameDeletedEvent = GameDeletedEventImpl
}
