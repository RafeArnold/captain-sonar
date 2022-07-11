package uk.co.rafearnold.captainsonar.config

import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.assertIterableEquals
import org.junit.jupiter.api.Test
import uk.co.rafearnold.commons.config.ListenEventImpl
import uk.co.rafearnold.commons.config.ObservableMap
import uk.co.rafearnold.commons.config.ObservableMutableMapImpl
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class ObservableJsonObjectTest {

    @Test
    @Suppress("ControlFlowWithEmptyBody")
    fun `listeners can be added and removed`() {
        val observableJsonObject =
            ObservableJsonObject(ObservableMutableMapImpl(backingMap = ConcurrentHashMap()))

        val key1 = "test_key1"
        val key2 = "test_key2"
        val key3 = "test_key3"
        val value1 = "test_value1"
        val value2 = "test_value2"
        val value3 = "test_value3"

        val listener1Events: MutableList<ObservableMap.ListenEvent<String, Any>> = mutableListOf()
        val listener1Id: String = observableJsonObject.addListener(key1) { listener1Events.add(it) }

        assertIterableEquals(emptyList<ObservableMap.ListenEvent<String, Any>>(), listener1Events)

        observableJsonObject.put(key1, value1)

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
                listOf(ListenEventImpl(key = key1, oldValue = null, newValue = value1))
            CompletableFuture.runAsync { while (listener1Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener1Events)
        }

        observableJsonObject.put(key1, value2)

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
                listOf(
                    ListenEventImpl(key = key1, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key1, oldValue = value1, newValue = value2)
                )
            CompletableFuture.runAsync { while (listener1Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener1Events)
        }

        observableJsonObject.remove(key1)

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
                listOf(
                    ListenEventImpl(key = key1, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key1, oldValue = value1, newValue = value2),
                    ListenEventImpl(key = key1, oldValue = value2, newValue = null)
                )
            CompletableFuture.runAsync { while (listener1Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener1Events)
        }

        val listener2Events: MutableList<ObservableMap.ListenEvent<String, Any>> = mutableListOf()
        observableJsonObject.addListener(key1) { listener2Events.add(it) }

        observableJsonObject.put(key1, value3)

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
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
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
                listOf(ListenEventImpl(key = key1, oldValue = null, newValue = value3))
            CompletableFuture.runAsync { while (listener2Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener2Events)
        }

        val listener3Events: MutableList<ObservableMap.ListenEvent<String, Any>> = mutableListOf()
        val listener3Id: String = observableJsonObject.addListener(key2) { listener3Events.add(it) }

        observableJsonObject.put(key2, value1)

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
                listOf(ListenEventImpl(key = key2, oldValue = null, newValue = value1))
            CompletableFuture.runAsync { while (listener3Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener3Events)
        }

        observableJsonObject.mergeIn(JsonObject(mapOf(key1 to value1, key2 to value2, key3 to value3)))

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
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
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
                listOf(
                    ListenEventImpl(key = key1, oldValue = null, newValue = value3),
                    ListenEventImpl(key = key1, oldValue = value3, newValue = value1)
                )
            CompletableFuture.runAsync { while (listener2Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener2Events)
        }
        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
                listOf(
                    ListenEventImpl(key = key2, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key2, oldValue = value1, newValue = value2)
                )
            CompletableFuture.runAsync { while (listener3Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener3Events)
        }

        observableJsonObject.removeListener(listener1Id)

        observableJsonObject.put(key1, value2)

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
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
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
                listOf(
                    ListenEventImpl(key = key1, oldValue = null, newValue = value3),
                    ListenEventImpl(key = key1, oldValue = value3, newValue = value1),
                    ListenEventImpl(key = key1, oldValue = value1, newValue = value2)
                )
            CompletableFuture.runAsync { while (listener2Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener2Events)
        }

        observableJsonObject.removeListener(listener3Id)

        observableJsonObject.put(key2, value3)

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
                listOf(
                    ListenEventImpl(key = key2, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key2, oldValue = value1, newValue = value2)
                )
            CompletableFuture.runAsync { while (listener3Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener3Events)
        }

        val listener4Events: MutableList<ObservableMap.ListenEvent<String, Any>> = mutableListOf()
        observableJsonObject.addListener(key1) { listener4Events.add(it) }

        observableJsonObject.put(key1, value3)

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
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
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
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
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
                listOf(ListenEventImpl(key = key1, oldValue = value2, newValue = value3))
            CompletableFuture.runAsync { while (listener4Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener4Events)
        }

        val listener5Events: MutableList<ObservableMap.ListenEvent<String, Any>> = mutableListOf()
        observableJsonObject.addListener(key2) { listener5Events.add(it) }

        observableJsonObject.put(key2, value1)

        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
                listOf(
                    ListenEventImpl(key = key2, oldValue = null, newValue = value1),
                    ListenEventImpl(key = key2, oldValue = value1, newValue = value2)
                )
            CompletableFuture.runAsync { while (listener3Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener3Events)
        }
        run {
            val expectedEvents: List<ObservableMap.ListenEvent<String, Any>> =
                listOf(ListenEventImpl(key = key2, oldValue = value3, newValue = value1))
            CompletableFuture.runAsync { while (listener5Events.size != expectedEvents.size); }
                .get(2, TimeUnit.SECONDS)
            assertIterableEquals(expectedEvents, listener5Events)
        }
    }
}
