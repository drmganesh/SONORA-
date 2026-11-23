package domain.player

import android.media.AudioManager

/**
 * Pure, deterministic state machine governing Android audio focus, phone call interruptions,
 * ducking, manual pause during a call, explicit user stop, and noisy/Bluetooth disconnection.
 *
 * Designed so both [com.example.service.SonoraMusicService] and JVM unit tests share the exact
 * same focus transition rules without requiring `READ_PHONE_STATE` or call-log permissions.
 */
class PlaybackAudioFocusController {

    sealed class Action {
        data object None : Action()
        data class PauseForTransientLoss(
            val keepAudioFocusRequest: Boolean = true,
            val saveExactPosition: Boolean = true
        ) : Action()
        data class PauseForPermanentLoss(val abandonFocus: Boolean = true) : Action()
        data class DuckVolume(val targetVolume: Float = 0.25f) : Action()
        data class RestoreVolumeOnly(val targetVolume: Float = 1.0f) : Action()
        data class ResumeAfterFocusGain(val targetVolume: Float = 1.0f) : Action()
        data class PauseForBecomingNoisy(val abandonFocus: Boolean = true) : Action()
    }

    var inTransientFocusLoss: Boolean = false
        private set

    var shouldResumeOnFocusGain: Boolean = false
        private set

    var manuallyPausedDuringFocusLoss: Boolean = false
        private set

    var explicitlyStopped: Boolean = false
        private set

    var isDucked: Boolean = false
        private set

    fun onUserStartedOrResumedPlayback() {
        explicitlyStopped = false
        inTransientFocusLoss = false
        shouldResumeOnFocusGain = false
        manuallyPausedDuringFocusLoss = false
        isDucked = false
    }

    fun onUserManualPause() {
        if (inTransientFocusLoss) {
            manuallyPausedDuringFocusLoss = true
        }
        shouldResumeOnFocusGain = false
    }

    fun onUserExplicitStop() {
        explicitlyStopped = true
        inTransientFocusLoss = false
        shouldResumeOnFocusGain = false
        manuallyPausedDuringFocusLoss = false
        isDucked = false
    }

    fun onAudioBecomingNoisy(headsetAutoPauseEnabled: Boolean): Action {
        if (!headsetAutoPauseEnabled) return Action.None
        shouldResumeOnFocusGain = false
        inTransientFocusLoss = false
        isDucked = false
        return Action.PauseForBecomingNoisy(abandonFocus = true)
    }

    fun onAudioFocusChange(
        focusChange: Int,
        isCurrentlyPlaying: Boolean,
        resumeAfterPhoneCallEnabled: Boolean
    ): Action {
        return when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    if (isCurrentlyPlaying) {
                        isDucked = true
                        Action.DuckVolume(targetVolume = 0.25f)
                    } else {
                        Action.None
                    }
                } else {
                    // Incoming call, outgoing call, call waiting, or temporary system interruption.
                    // CRITICAL: Do NOT abandon the AudioFocusRequest here so AUDIOFOCUS_GAIN fires
                    // when the call or transient interruption ends.
                    val wasAlreadyWaitingForResume = inTransientFocusLoss && shouldResumeOnFocusGain
                    inTransientFocusLoss = true
                    if (!manuallyPausedDuringFocusLoss && !explicitlyStopped) {
                        shouldResumeOnFocusGain =
                            (isCurrentlyPlaying || wasAlreadyWaitingForResume) && resumeAfterPhoneCallEnabled
                    }
                    if (isCurrentlyPlaying) {
                        Action.PauseForTransientLoss(
                            keepAudioFocusRequest = true,
                            saveExactPosition = true
                        )
                    } else {
                        Action.None
                    }
                }
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                val shouldResume = inTransientFocusLoss &&
                    shouldResumeOnFocusGain &&
                    resumeAfterPhoneCallEnabled &&
                    !manuallyPausedDuringFocusLoss &&
                    !explicitlyStopped
                val wasDucked = isDucked

                inTransientFocusLoss = false
                shouldResumeOnFocusGain = false
                manuallyPausedDuringFocusLoss = false
                isDucked = false

                when {
                    shouldResume -> Action.ResumeAfterFocusGain(targetVolume = 1.0f)
                    wasDucked -> Action.RestoreVolumeOnly(targetVolume = 1.0f)
                    else -> Action.None
                }
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                // Another media app took permanent audio focus.
                inTransientFocusLoss = false
                shouldResumeOnFocusGain = false
                manuallyPausedDuringFocusLoss = false
                isDucked = false
                Action.PauseForPermanentLoss(abandonFocus = true)
            }

            else -> Action.None
        }
    }
}
