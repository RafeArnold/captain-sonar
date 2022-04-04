package uk.co.rafearnold.captainsonar.model.factory

import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.Player

interface GameFactory {
    fun create(id: String, hostId: String, players: Map<String, Player>, started: Boolean): Game
}
