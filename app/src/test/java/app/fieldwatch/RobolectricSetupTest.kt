package app.fieldwatch

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** P1-A1: Robolectric runs offline on the pinned SDK, with the app's resources and a plain Application. */
@RunWith(RobolectricTestRunner::class)
class RobolectricSetupTest {
    @Test
    fun runsOnThePinnedSdkWithTheAppResources() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(android.app.Application::class.java, context.javaClass) // not FieldwatchApp
        // robolectric.properties pins the app's targetSdk; a targetSdk bump must move the pin (and its jar) too.
        assertEquals(context.applicationInfo.targetSdkVersion, android.os.Build.VERSION.SDK_INT)
        assertEquals("Spectre", context.getString(R.string.app_name))
    }
}
