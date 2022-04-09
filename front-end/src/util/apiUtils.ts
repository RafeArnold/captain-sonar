import {GameEvent} from "../model/GameEvent";
import GetGameStateResponse from "../model/GetGameStateResponse";
import JoinGameResponse from "../model/JoinGameResponse";
import CreateGameResponse from "../model/CreateGameResponse";
import StartGameResponse from "../model/StartGameResponse";

export function doGetGameStateRequest(): Promise<GetGameStateResponse> {
    return doRequest({path: "/v1/game/state", method: "GET"}).then(value => value.json())
}

export function doJoinGameRequest(gameId: string, playerName: string): Promise<JoinGameResponse> {
    return doRequest({path: "/v1/game/join", method: "POST", body: {gameId, playerName}}).then(value => value.json())
}

export function doCreateGameRequest(hostName: string): Promise<CreateGameResponse> {
    return doRequest({path: "/v1/game/create", method: "POST", body: {hostName}}).then(value => value.json())
}

export function doStartGameRequest(): Promise<StartGameResponse> {
    return doRequest({path: "/v1/game/start", method: "POST"}).then(value => value.json())
}

export function subscribeToGameEvents(eventHandler: (gameEvent: GameEvent) => void) {
    const eventSource = new EventSource(constructUrl("/v1/game/stream"))
    eventSource.onmessage = event => eventHandler(JSON.parse(event.data))
}

function doRequest({path, method, body, queryParams}: Request) {
    const init: RequestInit = {method: method}
    if (body) {
        init["body"] = JSON.stringify(body)
    }
    return window.fetch(constructUrl(path, queryParams), init)
}

function constructUrl(path: string, queryParams?: Map<string, string>): string {
    const url = new URL(location.origin + path)
    queryParams?.forEach((paramValue: string, paramName: string) => url.searchParams.append(paramName, paramValue))
    return url.toString()
}

interface Request {
    path: string
    method: string
    body?: any
    queryParams?: Map<string, string>
}
