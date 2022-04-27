package uk.co.rafearnold.captainsonar

import uk.co.rafearnold.captainsonar.common.Subscription
import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.GameEvent
import java.util.function.Consumer

interface GameService {

    fun getGame(gameId: String): Game?

    fun createGame(hostId: String, hostName: String): Game

    fun addPlayer(gameId: String, playerId: String, playerName: String): Game

    fun timeoutPlayer(gameId: String, playerId: String): Game

    fun startGame(gameId: String, playerId: String): Game

    fun endGame(gameId: String, playerId: String)

    fun addGameListener(gameId: String, consumer: Consumer<GameEvent>): Subscription
}
