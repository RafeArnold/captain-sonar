package uk.co.rafearnold.captainsonar.model.mapper

import uk.co.rafearnold.captainsonar.event.model.GameEventEventApiV1Model
import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.GameEvent
import uk.co.rafearnold.captainsonar.repository.StoredGame

interface ModelMapper {

    fun mapToGame(gameId: String, storedGame: StoredGame): Game

    fun mapToStoredGame(game: Game): StoredGame

    fun mapToGameEventEventApiV1Model(gameId: String, event: GameEvent): GameEventEventApiV1Model

    fun mapToGameEventPair(event: GameEventEventApiV1Model): Pair<String, GameEvent>
}
