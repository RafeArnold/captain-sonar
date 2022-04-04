package uk.co.rafearnold.captainsonar.model

data class GameStartedEventImpl(
    override val game: Game
) : GameStartedEvent
