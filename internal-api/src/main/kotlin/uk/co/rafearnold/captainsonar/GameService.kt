package uk.co.rafearnold.captainsonar

import uk.co.rafearnold.captainsonar.model.Game

interface GameService {

    fun getGame(gameId: String): Game?

    fun createGame(hostId: String, hostName: String): Game

    fun addPlayer(gameId: String, playerId: String, playerName: String): Game

    fun timeoutPlayer(gameId: String, playerId: String): Game

    fun startGame(gameId: String, playerId: String): Game

    fun endGame(gameId: String, playerId: String)

    fun addGameListener(gameId: String, listener: GameListener): String

    fun removeGameListener(gameId: String, listenerId: String)
}
