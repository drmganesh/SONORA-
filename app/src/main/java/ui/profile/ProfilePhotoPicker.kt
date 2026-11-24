package ui.profile

import android.content.ActivityNotFoundException
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val PHOTO_PICKER_TAG = "ProfilePhotoPicker"

/**
 * Zero-permission image picker launcher for SONORA profile avatars.
 * Uses Android Photo Picker (PickVisualMedia) whenever available, and falls back to the
 * system document picker (OpenDocument -> GetContent) on API 26–30 devices without a
 * backported Photo Picker. Never requests broad storage permissions.
 */
@Composable
fun rememberProfilePhotoPickerLauncher(
    onPhotoSelected: (Uri) -> Unit,
    onPickerUnavailable: () -> Unit = {}
): () -> Unit {
    val context = LocalContext.current

    val visualMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPhotoSelected(uri)
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onPhotoSelected(uri)
        }
    }

    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onPhotoSelected(uri)
        }
    }

    return remember(context, visualMediaLauncher, openDocumentLauncher, getContentLauncher) {
        {
            val usePhotoPicker = runCatching {
                ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
            }.getOrDefault(false)

            if (usePhotoPicker) {
                try {
                    visualMediaLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } catch (e: ActivityNotFoundException) {
                    Log.w(PHOTO_PICKER_TAG, "Photo Picker unavailable, falling back to document picker", e)
                    launchDocumentFallback(openDocumentLauncher, getContentLauncher, onPickerUnavailable)
                } catch (e: SecurityException) {
                    Log.w(PHOTO_PICKER_TAG, "Photo Picker launch denied, falling back to document picker", e)
                    launchDocumentFallback(openDocumentLauncher, getContentLauncher, onPickerUnavailable)
                }
            } else {
                launchDocumentFallback(openDocumentLauncher, getContentLauncher, onPickerUnavailable)
            }
        }
    }
}

private fun launchDocumentFallback(
    openDocumentLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Uri?>,
    getContentLauncher: androidx.activity.compose.ManagedActivityResultLauncher<String, Uri?>,
    onPickerUnavailable: () -> Unit
) {
    try {
        openDocumentLauncher.launch(arrayOf("image/*"))
    } catch (e: ActivityNotFoundException) {
        Log.w(PHOTO_PICKER_TAG, "OpenDocument unavailable, trying GetContent fallback", e)
        try {
            getContentLauncher.launch("image/*")
        } catch (fallbackErr: ActivityNotFoundException) {
            Log.w(PHOTO_PICKER_TAG, "No compatible system image picker available", fallbackErr)
            onPickerUnavailable()
        } catch (t: Throwable) {
            Log.w(PHOTO_PICKER_TAG, "Failed opening image picker fallback", t)
            onPickerUnavailable()
        }
    } catch (t: Throwable) {
        Log.w(PHOTO_PICKER_TAG, "Failed opening document picker", t)
        onPickerUnavailable()
    }
}
