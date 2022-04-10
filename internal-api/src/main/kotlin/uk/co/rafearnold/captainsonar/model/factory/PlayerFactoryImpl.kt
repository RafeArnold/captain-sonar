package uk.co.rafearnold.captainsonar.model.factory

import uk.co.rafearnold.captainsonar.model.Player
import uk.co.rafearnold.captainsonar.model.PlayerImpl

class PlayerFactoryImpl : PlayerFactory {

    override fun create(name: String): Player =
        PlayerImpl(name = name)
}
