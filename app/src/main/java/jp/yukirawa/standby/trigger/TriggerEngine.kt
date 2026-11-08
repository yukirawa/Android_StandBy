package jp.yukirawa.standby.trigger

/**
 * Pure Kotlin trigger state machine. No Android dependencies so every transition is unit-testable.
 */
enum class TriggerState {
    DISABLED,
    IDLE,
    ARMED,
    VISIBLE,
    SUPPRESSED,
}

enum class Suppression {
    NONE,
    USER,
    POWER,
    UNLOCKED,
}

enum class TriggerAction {
    NONE,
    LAUNCH,
    FINISH,
}

data class TriggerInput(
    val autoLaunch: Boolean = true,
    val charging: Boolean = false,
    val landscape: Boolean = false,
    val screenOn: Boolean = true,
    val locked: Boolean = false,
    val visible: Boolean = false,
)

data class TriggerOutput(
    val state: TriggerState,
    val suppression: Suppression,
    val action: TriggerAction,
    val reason: String,
)

class TriggerEngine {
    var state: TriggerState = TriggerState.DISABLED
        private set
    var suppression: Suppression = Suppression.NONE
        private set

    private var launchRequested = false
    private var previousCharging: Boolean? = null

    fun evaluate(input: TriggerInput): TriggerOutput {
        if (!input.autoLaunch) {
            val action = if (input.visible) TriggerAction.FINISH else TriggerAction.NONE
            reset()
            state = TriggerState.DISABLED
            return output(action, "自動起動がオフ")
        }

        if (previousCharging == false && input.charging && suppression == Suppression.USER) {
            suppression = Suppression.NONE
        }
        previousCharging = input.charging

        if (!input.charging) {
            suppression = Suppression.NONE
            launchRequested = false
            val action = if (input.visible) TriggerAction.FINISH else TriggerAction.NONE
            state = TriggerState.IDLE
            return output(action, if (action == TriggerAction.FINISH) "充電解除" else "充電待ち")
        }

        when (suppression) {
            Suppression.USER -> {
                if (!input.landscape) {
                    suppression = Suppression.NONE
                } else {
                    state = TriggerState.SUPPRESSED
                    return output(TriggerAction.NONE, "ユーザー操作のため再表示を抑制中")
                }
            }

            Suppression.POWER -> {
                if (input.screenOn) {
                    suppression = Suppression.NONE
                } else {
                    state = TriggerState.SUPPRESSED
                    return output(TriggerAction.NONE, "画面オフのため復帰待ち")
                }
            }

            Suppression.UNLOCKED -> {
                if (!input.screenOn) {
                    suppression = Suppression.NONE
                } else {
                    state = TriggerState.SUPPRESSED
                    return output(TriggerAction.NONE, "ロック解除のため待機")
                }
            }

            Suppression.NONE -> Unit
        }

        if (!input.landscape) {
            launchRequested = false
            val action = if (input.visible) TriggerAction.FINISH else TriggerAction.NONE
            state = TriggerState.ARMED
            return output(action, if (action == TriggerAction.FINISH) "縦向き" else "横向き待ち")
        }

        if (input.visible && input.screenOn && !input.locked) {
            launchRequested = false
            suppression = Suppression.UNLOCKED
            state = TriggerState.SUPPRESSED
            return output(TriggerAction.FINISH, "ロック解除")
        }

        if (input.visible) {
            launchRequested = false
            state = TriggerState.VISIBLE
            return output(TriggerAction.NONE, "表示中")
        }

        val eligible = !input.screenOn || input.locked
        if (!eligible) {
            launchRequested = false
            state = TriggerState.ARMED
            return output(TriggerAction.NONE, "画面オン・ロック解除中のため待機")
        }

        state = TriggerState.VISIBLE
        if (launchRequested) {
            return output(TriggerAction.NONE, "起動待ち")
        }
        launchRequested = true
        return output(TriggerAction.LAUNCH, "表示条件を満たしました")
    }

    fun onActivityStarted(): TriggerOutput {
        launchRequested = false
        state = TriggerState.VISIBLE
        return output(TriggerAction.NONE, "表示開始")
    }

    fun onLaunchFailed(): TriggerOutput {
        launchRequested = false
        state = TriggerState.ARMED
        return output(TriggerAction.NONE, "起動失敗")
    }

    fun onUserDismissed(): TriggerOutput {
        launchRequested = false
        suppression = Suppression.USER
        state = TriggerState.SUPPRESSED
        return output(TriggerAction.NONE, "ユーザーが閉じたため抑制")
    }

    fun onPowerOffWhileVisible(): TriggerOutput {
        launchRequested = false
        suppression = Suppression.POWER
        state = TriggerState.SUPPRESSED
        return output(TriggerAction.NONE, "画面オフのため抑制")
    }

    /**
     * The activity disappeared because the user unlocked the device (or another app took over
     * while the screen stayed on). This is not a user dismissal: stay quiet until the screen
     * turns off again, then allow the next automatic launch.
     */
    fun onUnlocked(): TriggerOutput {
        launchRequested = false
        suppression = Suppression.UNLOCKED
        state = TriggerState.SUPPRESSED
        return output(TriggerAction.NONE, "ロック解除のため待機")
    }

    fun onVisibleLost(): TriggerOutput {
        launchRequested = false
        suppression = Suppression.USER
        state = TriggerState.SUPPRESSED
        return output(TriggerAction.NONE, "表示が失われたため抑制")
    }

    fun reset() {
        launchRequested = false
        suppression = Suppression.NONE
        previousCharging = null
        state = TriggerState.DISABLED
    }

    private fun output(action: TriggerAction, reason: String) = TriggerOutput(
        state = state,
        suppression = suppression,
        action = action,
        reason = reason,
    )
}
