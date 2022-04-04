import {config} from "../config";

export function doCreateGameRequest(hostName) {
    return doRequest({path: "/v1/game/create", method: "POST", body: {hostName}})
}

function doRequest({path, method, body, queryParams}) {
    const url = new URL(config.apiBaseUrl + path)
    for (const paramName in queryParams) {
        url.searchParams.append(paramName, queryParams[paramName])
    }
    const init = {method: method, credentials: "include"}
    if (body) {
        init["body"] = JSON.stringify(body)
    }
    return window.fetch(url, init)
}
