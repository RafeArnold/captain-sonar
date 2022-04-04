package uk.co.rafearnold.captainsonar.model.factory

import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.GameImpl
import uk.co.rafearnold.captainsonar.model.Player

class GameFactoryImpl : GameFactory {

    override fun create(
        id: String,
        hostId: String,
        players: Map<String, Player>,
        started: Boolean
    ): Game =
        GameImpl(id = id, hostId = hostId, players = players, started = started)
}
