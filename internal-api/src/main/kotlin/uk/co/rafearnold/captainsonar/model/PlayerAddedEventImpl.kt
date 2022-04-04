package uk.co.rafearnold.captainsonar.model

data class PlayerAddedEventImpl(
    override val game: Game
) : PlayerAddedEvent
