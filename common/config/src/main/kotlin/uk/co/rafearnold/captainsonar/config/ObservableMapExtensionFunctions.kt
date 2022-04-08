package uk.co.rafearnold.captainsonar.config

fun <V> ObservableMap<String, V>.addListener(keyRegex: String, listener: ObservableMap.Listener<String, V>): String {
    val regex = Regex(keyRegex)
    return this.addListener({ regex.matches(it) }, listener)
}
