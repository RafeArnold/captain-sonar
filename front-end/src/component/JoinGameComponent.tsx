import React from "react";
import GameState from "../model/GameState";
import {doJoinGameRequest} from "../util/apiUtils";

export default function JoinGameComponent({setGameState}: JoinGameComponentProps) {

    const [gameIdInputValue, setGameIdInputValue] = React.useState("")
    const [nameInputValue, setNameInputValue] = React.useState("")

    function handleGameIdInputChange(event: React.ChangeEvent<HTMLInputElement>) {
        setGameIdInputValue(event.target.value)
    }

    function handleNameInputChange(event: React.ChangeEvent<HTMLInputElement>) {
        setNameInputValue(event.target.value)
    }

    function handleJoinGameButtonClick() {
        doJoinGameRequest(gameIdInputValue, nameInputValue)
            .then(res => setGameState(res.gameState))
    }

    return (
        <>
            <label>game id </label>
            <input onChange={event => handleGameIdInputChange(event)} value={gameIdInputValue}/>
            <br/>
            <label>name </label>
            <input onChange={event => handleNameInputChange(event)} value={nameInputValue}/>
            <br/>
            <button onClick={handleJoinGameButtonClick}>join game</button>
        </>
    )
}

type JoinGameComponentProps = {
    setGameState: (gameState: GameState) => void
}
