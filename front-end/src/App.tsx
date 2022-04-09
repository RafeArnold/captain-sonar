import React from "react";
import GameState from "./model/GameState";
import GameComponent from "./component/GameComponent";
import NewGameComponent from "./component/NewGameComponent";
import JoinGameComponent from "./component/JoinGameComponent";
import {doGetGameStateRequest} from "./util/apiUtils";

export default function App() {

    const [gameState, setGameState] = React.useState<GameState | null>(null)

    React.useEffect(checkIfInGame, [])

    function checkIfInGame() {
        doGetGameStateRequest()
            .then(({gameState}) => {
                if (gameState) setGameState(gameState)
            })
    }

    function endGame() {
        setGameState(null)
    }

    return (
        <>
            <h1>captain sonar</h1>
            {gameState ? <GameComponent gameState={gameState} setGameState={setGameState} endGame={endGame}/> :
                <>
                    <JoinGameComponent setGameState={setGameState}/>
                    <br/><br/>
                    <NewGameComponent setGameState={setGameState}/>
                </>}
        </>
    )
}
