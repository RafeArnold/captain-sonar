import React from "react";
import GameState from "../model/GameState";
import {doStartGameRequest} from "../util/apiUtils";
import useSubscribe from "../util/useSubscribe";

export default function GameComponent({gameState, setGameState, endGame}: GameComponentProps) {

    useSubscribe(setGameState, endGame)

    function handleStartGameButtonClick() {
        doStartGameRequest()
            .then(res => setGameState(res.gameState))
    }

    return (
        <>
            <h3>game id: {gameState.id}</h3>
            <h3>hosting: {gameState.hosting.toString()}</h3>
            <h3>players: {gameState.players.map(player => JSON.stringify(player)).toString()}</h3>
            <h3>started: {gameState.started.toString()}</h3>
            <button onClick={handleStartGameButtonClick}>start</button>
        </>
    )
}

type GameComponentProps = {
    gameState: GameState
    setGameState: (gameState: GameState) => void
    endGame: () => void
}
