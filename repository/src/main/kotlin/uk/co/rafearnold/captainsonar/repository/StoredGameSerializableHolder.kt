package uk.co.rafearnold.captainsonar.repository

import io.vertx.core.buffer.Buffer
import io.vertx.core.shareddata.ClusterSerializable

class StoredGameSerializableHolder private constructor(private var storedGame0: StoredGame?) : ClusterSerializable {

    companion object {
        fun create(storedGame: StoredGame): StoredGameSerializableHolder =
            StoredGameSerializableHolder(storedGame0 = storedGame)

        fun createFromBytes(bytes: ByteArray): StoredGameSerializableHolder {
            val buffer: Buffer = Buffer.buffer(bytes)
            val holder = StoredGameSerializableHolder()
            holder.readFromBuffer(0, buffer)
            return holder
        }
    }

    private constructor() : this(storedGame0 = null)

    val storedGame: StoredGame get() = storedGame0!!

    override fun writeToBuffer(buffer: Buffer) {
        val storedGame: StoredGame = storedGame
        buffer
            .appendStringWithLength(storedGame.hostId)
            .appendPlayers(storedGame.players)
            .appendBoolean(storedGame.started)
    }

    private fun Buffer.appendPlayers(players: Map<String, StoredPlayer>): Buffer {
        this.appendInt(players.size)
        for ((playerId: String, player: StoredPlayer) in players) {
            this.appendStringWithLength(playerId).appendPlayer(player)
        }
        return this
    }

    private fun Buffer.appendPlayer(player: StoredPlayer): Buffer = this.appendStringWithLength(player.name)

    private fun Buffer.appendStringWithLength(string: String): Buffer =
        string.toByteArray(Charsets.UTF_8).let { this.appendInt(it.size).appendBytes(it) }

    private fun Buffer.appendBoolean(boolean: Boolean): Buffer = this.appendByte(if (boolean) 1 else 0)

    override fun readFromBuffer(pos: Int, buffer: Buffer): Int {
        val hostId: Pair<String, Int> = readHostIdFromBuffer(buffer, pos)
        val players: Pair<Map<String, StoredPlayer>, Int> = readPlayersFromBuffer(buffer, hostId.second)
        val started: Pair<Boolean, Int> = readStartedFromBuffer(buffer, players.second)
        storedGame0 = StoredGame(hostId = hostId.first, players = players.first, started = started.first)
        return started.second
    }

    private fun readHostIdFromBuffer(buffer: Buffer, pos: Int): Pair<String, Int> {
        var pos0: Int = pos
        val hostIdLength: Int = buffer.getInt(pos0)
        pos0 += 4
        val hostId = String(buffer.getBytes(pos0, pos0 + hostIdLength), Charsets.UTF_8)
        pos0 += hostIdLength
        return hostId to pos0
    }

    private fun readPlayersFromBuffer(buffer: Buffer, pos: Int): Pair<Map<String, StoredPlayer>, Int> {
        var pos0: Int = pos
        var playerCount: Int = buffer.getInt(pos)
        pos0 += 4
        val players: MutableMap<String, StoredPlayer> = mutableMapOf()
        while (playerCount-- > 0) {
            val playerIdLength: Int = buffer.getInt(pos0)
            pos0 += 4
            val playerId = String(buffer.getBytes(pos0, pos0 + playerIdLength), Charsets.UTF_8)
            pos0 += playerIdLength
            val playerNameLength: Int = buffer.getInt(pos0)
            pos0 += 4
            val playerName = String(buffer.getBytes(pos0, pos0 + playerNameLength), Charsets.UTF_8)
            pos0 += playerNameLength
            val player = StoredPlayer(name = playerName)
            players[playerId] = player
        }
        return players to pos0
    }

    private fun readStartedFromBuffer(buffer: Buffer, pos: Int): Pair<Boolean, Int> =
        (buffer.getByte(pos) == 1.toByte()) to (pos + 1)
}
