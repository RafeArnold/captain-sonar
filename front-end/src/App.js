import React from "react";
import {doCreateGameRequest} from "./util/apiUtils"

export default function App() {

    const [nameInputValue, setNameInputValue] = React.useState("")

    function handleNameInputChange(event) {
        setNameInputValue(event.target.value)
    }

    function handleNewGameButtonClick() {
        doCreateGameRequest(nameInputValue)
    }

    return (
        <>
            <h1>captain sonar</h1>
            <input onChange={handleNameInputChange} value={nameInputValue}/>
            <button onClick={handleNewGameButtonClick}>new game</button>
        </>
    )
}
