package uk.co.rafearnold.captainsonar.model.factory

import uk.co.rafearnold.captainsonar.model.Player

interface PlayerFactory {
    fun create(name: String): Player
}
