package uk.co.rafearnold.captainsonar.model.mapper

import uk.co.rafearnold.captainsonar.model.Game
import uk.co.rafearnold.captainsonar.model.Player
import uk.co.rafearnold.captainsonar.model.factory.GameFactory
import uk.co.rafearnold.captainsonar.model.factory.PlayerFactory
import uk.co.rafearnold.captainsonar.repository.StoredGame
import uk.co.rafearnold.captainsonar.repository.StoredPlayer
import javax.inject.Inject

class ModelMapperImpl @Inject constructor(
    private val gameFactory: GameFactory,
    private val playerFactory: PlayerFactory
) : ModelMapper {

    override fun mapToMutableGame(gameId: String, storedGame: StoredGame): Game =
        gameFactory.create(
            id = gameId,
            hostId = storedGame.hostId,
            players = storedGame.players
                .mapValues { (playerId: String, storedPlayer: StoredPlayer) ->
                    mapToPlayer(playerId = playerId, storedPlayer = storedPlayer)
                },
            started = storedGame.started
        )

    private fun mapToPlayer(playerId: String, storedPlayer: StoredPlayer) =
        playerFactory.create(id = playerId, name = storedPlayer.name)

    override fun mapToStoredGame(game: Game): StoredGame =
        StoredGame(
            hostId = game.hostId,
            players = game.players.mapValues { (_, player: Player) -> mapToStoredPlayer(player) },
            started = game.started
        )

    private fun mapToStoredPlayer(player: Player) =
        StoredPlayer(name = player.name)
}
