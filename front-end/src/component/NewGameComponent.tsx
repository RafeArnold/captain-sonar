import React from "react";
import GameState from "../model/GameState";
import {doCreateGameRequest} from "../util/apiUtils";

export default function NewGameComponent({setGameState}: NewGameComponentProps) {

    const [nameInputValue, setNameInputValue] = React.useState("")

    function handleNameInputChange(event: React.ChangeEvent<HTMLInputElement>) {
        setNameInputValue(event.target.value)
    }

    function handleNewGameButtonClick() {
        doCreateGameRequest(nameInputValue)
            .then(res => setGameState(res.gameState))
    }

    return (
        <>
            <label>name </label>
            <input onChange={event => handleNameInputChange(event)} value={nameInputValue}/>
            <br/>
            <button onClick={handleNewGameButtonClick}>new game</button>
        </>
    )
}

type NewGameComponentProps = {
    setGameState: (gameState: GameState) => void
}
