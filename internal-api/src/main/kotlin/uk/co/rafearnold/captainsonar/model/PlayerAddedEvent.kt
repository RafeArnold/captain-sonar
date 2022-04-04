package uk.co.rafearnold.captainsonar.model

interface PlayerAddedEvent : GameEvent {
    val game: Game
}
