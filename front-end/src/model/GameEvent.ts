import GameState from "./GameState";

export interface GameEvent {
    eventType: GameEventType
}

type GameEventType = "game-ended" | "game-started" | "player-joined"

export interface GameStartedEvent extends GameEvent {
    gameState: GameState
}

export interface PlayerJoinedEvent extends GameEvent {
    gameState: GameState
}
