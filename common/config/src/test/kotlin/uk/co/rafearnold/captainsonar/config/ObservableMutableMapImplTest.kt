package uk.co.rafearnold.captainsonar.config

import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ObservableMutableMapImplTest {

    @Test
    @Suppress("ControlFlowWithEmptyBody")
    fun `listeners can be added and removed`() {
        val observableMap: ObservableMutableMapImpl<String, String> =
            ObservableMutableMapImpl(backingMap = ConcurrentHashMap())

        val key1 = "test_key1"
        val key2 = "test_key2"
        val key3 = "test_key3"
        val value1 = "test_value1"
        val value2 = "test_value2"
        val value3 = "test_value3"

        val listener1Events: MutableList<ObservableMap.ListenEvent<String, String>> = mutableListOf()
        val listener1Id: String = observableMap.addListener({ it == key1 }, { listener1Events.add(it) })

        assertIterableEquals(emptyList<ObservableMap.ListenEvent<String, String>>(), listener1Events)

        observableMap[key1] = value1

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(ListenEventImpl(key = key1, oldValue = null, newValue = value1))
            CompletableFuture.runAsync { while (listener1Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener1Events)
        }

        observableMap[key1] = value2

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(
                    ListenEventImpl(key = key1, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key1, oldValue = value1, newValue = value2)
                )
            CompletableFuture.runAsync { while (listener1Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener1Events)
        }

        observableMap.remove(key1)

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(
                    ListenEventImpl(key = key1, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key1, oldValue = value1, newValue = value2),
                    ListenEventImpl(key = key1, oldValue = value2, newValue = null)
                )
            CompletableFuture.runAsync { while (listener1Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener1Events)
        }

        val listener2Events: MutableList<ObservableMap.ListenEvent<String, String>> = mutableListOf()
        observableMap.addListener({ it == key1 }, { listener2Events.add(it) })

        observableMap[key1] = value3

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(
                    ListenEventImpl(key = key1, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key1, oldValue = value1, newValue = value2),
                    ListenEventImpl(key = key1, oldValue = value2, newValue = null),
                    ListenEventImpl(key = key1, oldValue = null, newValue = value3)
                )
            CompletableFuture.runAsync { while (listener1Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener1Events)
        }
        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(ListenEventImpl(key = key1, oldValue = null, newValue = value3))
            CompletableFuture.runAsync { while (listener2Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener2Events)
        }

        val listener3Events: MutableList<ObservableMap.ListenEvent<String, String>> = mutableListOf()
        val listener3Id: String = observableMap.addListener({ it == key2 }, { listener3Events.add(it) })

        observableMap[key2] = value1

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(ListenEventImpl(key = key2, oldValue = null, newValue = value1))
            CompletableFuture.runAsync { while (listener3Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener3Events)
        }

        observableMap.putAll(mapOf(key1 to value1, key2 to value2, key3 to value3))

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(
                    ListenEventImpl(key = key1, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key1, oldValue = value1, newValue = value2),
                    ListenEventImpl(key = key1, oldValue = value2, newValue = null),
                    ListenEventImpl(key = key1, oldValue = null, newValue = value3),
                    ListenEventImpl(key = key1, oldValue = value3, newValue = value1)
                )
            CompletableFuture.runAsync { while (listener1Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener1Events)
        }
        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(
                    ListenEventImpl(key = key1, oldValue = null, newValue = value3),
                    ListenEventImpl(key = key1, oldValue = value3, newValue = value1)
                )
            CompletableFuture.runAsync { while (listener2Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener2Events)
        }
        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(
                    ListenEventImpl(key = key2, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key2, oldValue = value1, newValue = value2)
                )
            CompletableFuture.runAsync { while (listener3Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener3Events)
        }

        observableMap.removeListener(listener1Id)

        observableMap[key1] = value2

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(
                    ListenEventImpl(key = key1, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key1, oldValue = value1, newValue = value2),
                    ListenEventImpl(key = key1, oldValue = value2, newValue = null),
                    ListenEventImpl(key = key1, oldValue = null, newValue = value3),
                    ListenEventImpl(key = key1, oldValue = value3, newValue = value1)
                )
            CompletableFuture.runAsync { while (listener1Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener1Events)
        }
        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(
                    ListenEventImpl(key = key1, oldValue = null, newValue = value3),
                    ListenEventImpl(key = key1, oldValue = value3, newValue = value1),
                    ListenEventImpl(key = key1, oldValue = value1, newValue = value2)
                )
            CompletableFuture.runAsync { while (listener2Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener2Events)
        }

        observableMap.removeListener(listener3Id)

        observableMap[key2] = value3

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(
                    ListenEventImpl(key = key2, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key2, oldValue = value1, newValue = value2)
                )
            CompletableFuture.runAsync { while (listener3Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener3Events)
        }

        val listener4Events: MutableList<ObservableMap.ListenEvent<String, String>> = mutableListOf()
        observableMap.addListener({ it == key1 }, { listener4Events.add(it) })

        observableMap[key1] = value3

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(
                    ListenEventImpl(key = key1, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key1, oldValue = value1, newValue = value2),
                    ListenEventImpl(key = key1, oldValue = value2, newValue = null),
                    ListenEventImpl(key = key1, oldValue = null, newValue = value3),
                    ListenEventImpl(key = key1, oldValue = value3, newValue = value1)
                )
            CompletableFuture.runAsync { while (listener1Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener1Events)
        }
        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(
                    ListenEventImpl(key = key1, oldValue = null, newValue = value3),
                    ListenEventImpl(key = key1, oldValue = value3, newValue = value1),
                    ListenEventImpl(key = key1, oldValue = value1, newValue = value2),
                    ListenEventImpl(key = key1, oldValue = value2, newValue = value3)
                )
            CompletableFuture.runAsync { while (listener2Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener2Events)
        }
        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(ListenEventImpl(key = key1, oldValue = value2, newValue = value3))
            CompletableFuture.runAsync { while (listener4Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener4Events)
        }

        val listener5Events: MutableList<ObservableMap.ListenEvent<String, String>> = mutableListOf()
        observableMap.addListener({ it == key2 }, { listener5Events.add(it) })

        observableMap[key2] = value1

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(
                    ListenEventImpl(key = key2, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key2, oldValue = value1, newValue = value2)
                )
            CompletableFuture.runAsync { while (listener3Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener3Events)
        }
        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, String>> =
                listOf(ListenEventImpl(key = key2, oldValue = value3, newValue = value1))
            CompletableFuture.runAsync { while (listener5Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener5Events)
        }
    }
}
