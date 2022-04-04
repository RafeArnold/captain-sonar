package uk.co.rafearnold.captainsonar.model

interface GameStartedEvent : GameEvent {
    val game: Game
}
