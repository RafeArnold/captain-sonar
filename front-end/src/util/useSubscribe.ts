import GameState from "../model/GameState";
import React from "react";
import {subscribeToGameEvents} from "./apiUtils";
import {GameEvent, GameStartedEvent, PlayerJoinedEvent, PlayerTimedOutEvent} from "../model/GameEvent";

export default function useSubscribe(
    setGameState: (gameState: GameState) => void,
    endGame: () => void
) {

    React.useEffect(subscribeToEvents, [])

    function subscribeToEvents() {
        subscribeToGameEvents(handleGameEvent)
    }

    function handleGameEvent(gameEvent: GameEvent) {
        switch (gameEvent.eventType) {
            case "game-ended":
                endGame()
                break;
            case "game-started":
                setGameState((gameEvent as GameStartedEvent).gameState)
                break;
            case "player-joined":
                setGameState((gameEvent as PlayerJoinedEvent).gameState)
                break;
            case "player-timed-out":
                setGameState((gameEvent as PlayerTimedOutEvent).gameState)
                break;
        }
    }
}
