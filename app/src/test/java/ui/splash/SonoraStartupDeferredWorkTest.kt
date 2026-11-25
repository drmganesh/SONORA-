package ui.splash

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.viewmodel.SonoraViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that [SonoraViewModel] defers non-essential background work (online discovery fetches,
 * weather/festival calendar synchronization, download reconciliation, full recommendation scoring)
 * until the intro finishes or is skipped on warm launch / configuration change / process recreation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SonoraStartupDeferredWorkTest {

    @Test
    fun viewModel_defersNonCriticalStartupWorkUntilIntroCompletes_andIsIdempotentOnRecreation() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = SonoraViewModel(app)

        assertFalse(
            "Deferred background work gate must stay closed during initial cold-start intro frames",
            vm.isStartupDeferredWorkReleased
        )
        assertFalse(
            "Launch animation flag starts false on a fresh ViewModel session",
            vm.hasCompletedLaunchAnimation
        )
        assertEquals(
            "Online discovery must not trigger synchronous refresh during ViewModel construction",
            0L,
            vm.discoverRefreshCount.value
        )

        // Simulate intro handoff / completion
        vm.hasCompletedLaunchAnimation = true
        vm.onStartupIntroCompleted()
        assertTrue(
            "Calling onStartupIntroCompleted() must release deferred startup work",
            vm.isStartupDeferredWorkReleased
        )

        // Simulate subsequent warm return / configuration change call (idempotent)
        vm.onStartupIntroCompleted()
        assertTrue(vm.hasCompletedLaunchAnimation)
        assertTrue(vm.isStartupDeferredWorkReleased)
    }
}
