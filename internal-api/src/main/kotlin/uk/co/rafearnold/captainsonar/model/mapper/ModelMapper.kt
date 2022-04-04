package uk.co.rafearnold.captainsonar.model.mapper

import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.repository.StoredGame

interface ModelMapper {

    fun mapToMutableGame(gameId: String, storedGame: StoredGame): Game

    fun mapToStoredGame(game: Game): StoredGame
}
