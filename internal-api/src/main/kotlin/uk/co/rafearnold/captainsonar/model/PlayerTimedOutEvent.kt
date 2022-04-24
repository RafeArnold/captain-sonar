package uk.co.rafearnold.captainsonar.model

interface PlayerTimedOutEvent : GameEvent {
    val game: Game
}
