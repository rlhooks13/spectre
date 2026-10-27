package app.fieldwatch

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** P1-A1: coroutines-test and Turbine work on the plain JVM runner, where most of the suite runs. */
class CoroutineTestLibrariesTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun virtualTimeSkipsDelays() = runTest {
        val ticks = flow {
            repeat(3) {
                delay(60_000)
                emit(it)
            }
        }
        ticks.test {
            assertEquals(0, awaitItem())
            assertEquals(1, awaitItem())
            assertEquals(2, awaitItem())
            awaitComplete()
        }
        assertEquals(180_000L, currentTime)
    }

    @Test
    fun turbineSeesEachStateChange() = runTest {
        val state = MutableStateFlow("idle")
        state.test {
            assertEquals("idle", awaitItem())
            state.value = "scanning"
            assertEquals("scanning", awaitItem())
        }
    }
}
