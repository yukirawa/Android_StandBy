package jp.yukirawa.standby.trigger

import org.junit.Assert.assertEquals
import org.junit.Test

class TriggerEngineTest {
    private fun input(
        autoLaunch: Boolean = true,
        charging: Boolean = true,
        landscape: Boolean = false,
        screenOn: Boolean = true,
        locked: Boolean = false,
        visible: Boolean = false,
    ) = TriggerInput(
        autoLaunch = autoLaunch,
        charging = charging,
        landscape = landscape,
        screenOn = screenOn,
        locked = locked,
        visible = visible,
    )

    @Test
    fun `disabled when auto launch is off`() {
        val engine = TriggerEngine()
        val output = engine.evaluate(input(autoLaunch = false))
        assertEquals(TriggerState.DISABLED, output.state)
        assertEquals(TriggerAction.NONE, output.action)
    }

    @Test
    fun `visible screen is finished when auto launch turns off`() {
        val engine = TriggerEngine()
        val output = engine.evaluate(input(autoLaunch = false, visible = true))
        assertEquals(TriggerAction.FINISH, output.action)
    }

    @Test
    fun `waits for charging`() {
        val engine = TriggerEngine()
        val output = engine.evaluate(input(charging = false))
        assertEquals(TriggerState.IDLE, output.state)
        assertEquals(TriggerAction.NONE, output.action)
    }

    @Test
    fun `waits for landscape`() {
        val engine = TriggerEngine()
        val output = engine.evaluate(input(landscape = false))
        assertEquals(TriggerState.ARMED, output.state)
        assertEquals(TriggerAction.NONE, output.action)
    }

    @Test
    fun `launches when charging landscape and screen off`() {
        val engine = TriggerEngine()
        val output = engine.evaluate(input(landscape = true, screenOn = false))
        assertEquals(TriggerState.VISIBLE, output.state)
        assertEquals(TriggerAction.LAUNCH, output.action)
    }

    @Test
    fun `does not relaunch while already visible`() {
        val engine = TriggerEngine()
        engine.evaluate(input(landscape = true, screenOn = false))
        val output = engine.evaluate(input(landscape = true, screenOn = false, visible = true))
        assertEquals(TriggerState.VISIBLE, output.state)
        assertEquals(TriggerAction.NONE, output.action)
    }

    @Test
    fun `does not launch on unlocked screen`() {
        val engine = TriggerEngine()
        val output = engine.evaluate(input(landscape = true, screenOn = true, locked = false))
        assertEquals(TriggerState.ARMED, output.state)
        assertEquals(TriggerAction.NONE, output.action)
    }

    @Test
    fun `finishes when the device is unlocked while visible`() {
        val engine = TriggerEngine()
        engine.evaluate(input(landscape = true, screenOn = true, locked = true))
        val output = engine.evaluate(
            input(landscape = true, screenOn = true, locked = false, visible = true),
        )
        assertEquals(TriggerAction.FINISH, output.action)
    }

    @Test
    fun `unlocking does not relaunch while the screen stays on`() {
        val engine = TriggerEngine()
        engine.evaluate(input(landscape = true, screenOn = true, locked = true))
        engine.evaluate(input(landscape = true, screenOn = true, locked = false, visible = true))

        val relocked = engine.evaluate(input(landscape = true, screenOn = true, locked = true))
        assertEquals(TriggerState.SUPPRESSED, relocked.state)
        assertEquals(TriggerAction.NONE, relocked.action)
    }

    @Test
    fun `unlocking re-arms after the screen turns off`() {
        val engine = TriggerEngine()
        engine.evaluate(input(landscape = true, screenOn = true, locked = true))
        engine.evaluate(input(landscape = true, screenOn = true, locked = false, visible = true))

        val off = engine.evaluate(input(landscape = true, screenOn = false))
        assertEquals(TriggerAction.LAUNCH, off.action)
    }

    @Test
    fun `visible lost while unlocked is not a user dismissal`() {
        val engine = TriggerEngine()
        engine.evaluate(input(landscape = true, screenOn = true, locked = true))
        engine.onUnlocked()

        val on = engine.evaluate(input(landscape = true, screenOn = true, locked = false))
        assertEquals(TriggerState.SUPPRESSED, on.state)

        val off = engine.evaluate(input(landscape = true, screenOn = false))
        assertEquals(TriggerAction.LAUNCH, off.action)
    }

    @Test
    fun `launches on locked screen`() {
        val engine = TriggerEngine()
        val output = engine.evaluate(input(landscape = true, screenOn = true, locked = true))
        assertEquals(TriggerAction.LAUNCH, output.action)
    }

    @Test
    fun `user dismissal suppresses until portrait`() {
        val engine = TriggerEngine()
        engine.evaluate(input(landscape = true, screenOn = false))
        engine.onUserDismissed()

        val stillLandscape = engine.evaluate(input(landscape = true, screenOn = false))
        assertEquals(TriggerState.SUPPRESSED, stillLandscape.state)
        assertEquals(TriggerAction.NONE, stillLandscape.action)

        val portrait = engine.evaluate(input(landscape = false, screenOn = false))
        assertEquals(TriggerState.ARMED, portrait.state)

        val landscapeAgain = engine.evaluate(input(landscape = true, screenOn = false))
        assertEquals(TriggerAction.LAUNCH, landscapeAgain.action)
    }

    @Test
    fun `unplugging clears suppression`() {
        val engine = TriggerEngine()
        engine.evaluate(input(landscape = true, screenOn = false))
        engine.onUserDismissed()
        engine.evaluate(input(landscape = true, screenOn = false))
        engine.evaluate(input(charging = false))
        val output = engine.evaluate(input(landscape = true, screenOn = false))
        assertEquals(TriggerAction.LAUNCH, output.action)
    }

    @Test
    fun `power off while visible suppresses until screen on`() {
        val engine = TriggerEngine()
        engine.evaluate(input(landscape = true, screenOn = false))
        engine.onPowerOffWhileVisible()

        val off = engine.evaluate(input(landscape = true, screenOn = false))
        assertEquals(TriggerState.SUPPRESSED, off.state)

        val on = engine.evaluate(input(landscape = true, screenOn = true, locked = true))
        assertEquals(TriggerAction.LAUNCH, on.action)
    }

    @Test
    fun `finishes when charging stops while visible`() {
        val engine = TriggerEngine()
        engine.evaluate(input(landscape = true, screenOn = false))
        val output = engine.evaluate(input(charging = false, visible = true))
        assertEquals(TriggerAction.FINISH, output.action)
    }

    @Test
    fun `finishes when rotating to portrait while visible`() {
        val engine = TriggerEngine()
        engine.evaluate(input(landscape = true, screenOn = false))
        val output = engine.evaluate(input(landscape = false, visible = true))
        assertEquals(TriggerAction.FINISH, output.action)
    }

    @Test
    fun `launch failure re-arms and allows retry`() {
        val engine = TriggerEngine()
        val first = engine.evaluate(input(landscape = true, screenOn = false))
        assertEquals(TriggerAction.LAUNCH, first.action)

        engine.onLaunchFailed()
        assertEquals(TriggerState.ARMED, engine.state)

        // The engine itself allows a retry; the service applies the real cooldown.
        val second = engine.evaluate(input(landscape = true, screenOn = false))
        assertEquals(TriggerAction.LAUNCH, second.action)
    }
}
