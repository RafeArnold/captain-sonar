package uk.co.rafearnold.captainsonar.model.factory

import uk.co.rafearnold.captainsonar.model.Player

interface PlayerFactory {
    fun create(id: String, name: String): Player
}
