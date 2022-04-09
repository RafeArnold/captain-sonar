import Player from "./Player";

export default interface GameState {
    id: string,
    hosting: boolean,
    players: Array<Player>,
    started: boolean
}
