package uk.co.rafearnold.captainsonar.model

internal data class PlayerTimedOutEventImpl(
    override val game: Game
) : PlayerTimedOutEvent
