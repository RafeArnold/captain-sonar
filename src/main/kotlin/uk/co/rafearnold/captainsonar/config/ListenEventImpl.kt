package uk.co.rafearnold.captainsonar.config

data class ListenEventImpl<K, V>(
    override val key: K,
    override val oldValue: V?,
    override val newValue: V?
) : ObservableMap.ListenEvent<K, V>
