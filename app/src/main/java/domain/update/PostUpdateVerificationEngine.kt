package domain.update

/**
 * Pure, deterministic state transition engine for detecting genuinely completed updates
 * on app launch and governing the one-time post-update security reminder.
 *
 * Guarantees the reminder is ONLY shown when:
 * 1. The update was initiated through SONORA's official in-app update flow,
 * 2. The package name matches SONORA's installed package,
 * 3. The installed versionCode has strictly increased above the pre-install versionCode, AND
 * 4. The reminder has not already been dismissed for this installed versionCode.
 *
 * Guarantees the reminder is NEVER shown after:
 * - Failed installation
 * - Cancelled installation
 * - Normal app restart
 * - Unchanged version
 * - Downgrade
 */
object PostUpdateVerificationEngine {

    data class EvaluationResult(
        val showReminder: Boolean,
        val reminderState: PostUpdateReminderState? = null,
        val clearPendingSession: Boolean = false,
        val markVerifiedCompletedVersionCode: Long? = null,
        val shouldCleanUpdateCache: Boolean = false
    )

    fun evaluateOnLaunch(
        persisted: AppUpdatePersistedState,
        currentPackageName: String,
        currentVersionCode: Long,
        currentVersionName: String,
        isUnknownSourcesPermissionEnabled: Boolean
    ): EvaluationResult {
        // Case 1: Already verified on this version (e.g., configuration change before user dismissed it)
        if (persisted.verifiedCompletedUpdateVersionCode == currentVersionCode &&
            currentVersionCode > 0L &&
            persisted.lastReminderDismissedVersionCode < currentVersionCode
        ) {
            return EvaluationResult(
                showReminder = true,
                reminderState = PostUpdateReminderState(
                    installedVersionCode = currentVersionCode,
                    installedVersionName = currentVersionName,
                    isUnknownSourcesPermissionEnabled = isUnknownSourcesPermissionEnabled
                ),
                clearPendingSession = persisted.pendingInstallFromVersionCode > 0L,
                markVerifiedCompletedVersionCode = currentVersionCode,
                shouldCleanUpdateCache = true
            )
        }

        // No official pending update install session -> normal launch, never show reminder
        val fromVersion = persisted.pendingInstallFromVersionCode
        if (fromVersion <= 0L) {
            return EvaluationResult(showReminder = false)
        }

        // Package name must match SONORA's installed package
        if (!persisted.pendingInstallPackageName.equals(currentPackageName, ignoreCase = false)) {
            return EvaluationResult(
                showReminder = false,
                clearPendingSession = true
            )
        }

        // Reject unchanged version (normal restart, cancelled install, failed install) or downgrade
        if (currentVersionCode <= fromVersion) {
            return EvaluationResult(
                showReminder = false,
                clearPendingSession = true
            )
        }

        // Also ensure we did not downgrade below expected target if target was set, unless a newer official build succeeded
        if (persisted.pendingInstallTargetVersionCode > 0L &&
            currentVersionCode < persisted.pendingInstallTargetVersionCode
        ) {
            return EvaluationResult(
                showReminder = false,
                clearPendingSession = true
            )
        }

        // Already dismissed for this exact (or newer) versionCode -> never repeat
        if (persisted.lastReminderDismissedVersionCode >= currentVersionCode) {
            return EvaluationResult(
                showReminder = false,
                clearPendingSession = true,
                shouldCleanUpdateCache = true
            )
        }

        // Genuine successful update confirmed!
        return EvaluationResult(
            showReminder = true,
            reminderState = PostUpdateReminderState(
                installedVersionCode = currentVersionCode,
                installedVersionName = currentVersionName,
                isUnknownSourcesPermissionEnabled = isUnknownSourcesPermissionEnabled
            ),
            clearPendingSession = true,
            markVerifiedCompletedVersionCode = currentVersionCode,
            shouldCleanUpdateCache = true
        )
    }

    /**
     * Checks whether automatic update popup should be suppressed because the user tapped "Later"
     * for this same release version/tag.
     */
    fun isSuppressedByLaterChoice(
        persisted: AppUpdatePersistedState,
        releaseVersionCode: Long,
        releaseTag: String,
        isManualCheck: Boolean
    ): Boolean {
        if (isManualCheck) return false
        if (releaseVersionCode > 0L && persisted.dismissedUpdateVersionCode >= releaseVersionCode) {
            return true
        }
        if (releaseVersionCode <= 0L &&
            releaseTag.isNotBlank() &&
            persisted.dismissedUpdateTag.equals(releaseTag, ignoreCase = true)
        ) {
            return true
        }
        return false
    }
}
