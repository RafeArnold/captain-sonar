package uk.co.rafearnold.captainsonar.model.factory

import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.GameEndedEvent
import uk.co.rafearnold.captainsonar.model.GameEndedEventImpl
import uk.co.rafearnold.captainsonar.model.GameStartedEvent
import uk.co.rafearnold.captainsonar.model.GameStartedEventImpl
import uk.co.rafearnold.captainsonar.model.PlayerAddedEvent
import uk.co.rafearnold.captainsonar.model.PlayerAddedEventImpl
import uk.co.rafearnold.captainsonar.model.PlayerTimedOutEvent
import uk.co.rafearnold.captainsonar.model.PlayerTimedOutEventImpl

class GameEventFactoryImpl : GameEventFactory {

    override fun createPlayerAddedEvent(game: Game): PlayerAddedEvent = PlayerAddedEventImpl(game = game)

    override fun createPlayerTimedOutEvent(game: Game): PlayerTimedOutEvent = PlayerTimedOutEventImpl(game = game)

    override fun createGameStartedEvent(game: Game): GameStartedEvent = GameStartedEventImpl(game = game)

    override fun createGameEndedEvent(): GameEndedEvent = GameEndedEventImpl
}
