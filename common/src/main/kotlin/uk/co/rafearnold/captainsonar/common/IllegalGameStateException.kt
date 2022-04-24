package uk.co.rafearnold.captainsonar.common

sealed class IllegalGameStateException : IllegalStateException()

data class GameAlreadyExistsException(
    val gameId: String
) : IllegalGameStateException()

data class NoSuchGameFoundException(
    val gameId: String
) : IllegalGameStateException()

data class PlayerAlreadyJoinedGameException(
    val gameId: String,
    val playerId: String,
    val playerName: String
) : IllegalGameStateException()

data class NoSuchPlayerFoundException(
    val gameId: String,
    val playerId: String,
) : IllegalGameStateException()

data class GameAlreadyStartedException(
    val gameId: String
) : IllegalGameStateException()

data class UserIsNotHostException(
    val gameId: String,
    val playerId: String
) : IllegalGameStateException()
