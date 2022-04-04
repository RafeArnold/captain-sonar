package uk.co.rafearnold.captainsonar.config

import io.vertx.core.json.JsonObject

class ObservableJsonObject(backingMap: ObservableMutableMap<String, Any>) : JsonObject(backingMap) {

    override fun getMap(): ObservableMutableMap<String, Any> = super.getMap() as ObservableMutableMap<String, Any>

    fun addListener(keyRegex: String, listener: ObservableMap.Listener<String, Any>): String =
        map.addListener(keyRegex = keyRegex, listener = listener)

    fun removeListener(listenerId: String) = map.removeListener(listenerId = listenerId)
}
