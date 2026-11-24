package com.matchitcampus.app.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.matchitcampus.app.R
import com.matchitcampus.app.ui.theme.MatchItCampusTheme

private const val TAG = "MatchIt/PermissionGate"

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * Wraps anything that needs the location permission. [content] gets a `requestLocation` function
 * to call from a button; [onGranted] runs once location may be used.
 *
 * The Android permission flow, step by step:
 * 1. **Already granted** → [onGranted] straight away.
 * 2. **Denied before** (`shouldShowRequestPermissionRationale` is true) → explain why first in an
 *    `AlertDialog`, then ask again.
 * 3. **Otherwise** → the system dialog, through `rememberLauncherForActivityResult` (the Activity
 *    Result API, which also survives rotation while the system dialog is open).
 * 4. **Denied and the system won't ask again** ("Don't allow" twice) → only the app's Settings
 *    page can grant it now, so a dialog offers "Open settings".
 *
 * Both fine and coarse are requested together: on Android 12+ the system dialog then offers
 * "Precise" or "Approximate". Approximate still counts as granted; [onGranted] gets
 * `precise = false` so the caller can say the position may be off.
 *
 * The dialogs ([PermissionRationaleDialog], [PermissionSettingsDialog]) are stateless and
 * previewable; only this wrapper holds state.
 */
@Composable
fun LocationPermissionGate(
    onGranted: (precise: Boolean) -> Unit,
    content: @Composable (requestLocation: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // The launcher's callback can run after a recomposition; always call the newest onGranted.
    val currentOnGranted by rememberUpdatedState(onGranted)
    var showRationale by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        val fine = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        Log.d(TAG, "Location permission result: fine=$fine coarse=$coarse")
        when {
            fine || coarse -> currentOnGranted(fine)
            // No rationale after a denial means the system won't show its dialog again.
            activity != null && LOCATION_PERMISSIONS.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) } ->
                showSettings = true
            // A plain "Don't allow": nothing to do; the next tap shows the rationale first.
        }
    }

    val requestLocation: () -> Unit = {
        when {
            hasLocationPermission(context) -> currentOnGranted(isPrecise(context))
            activity != null && LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) } ->
                showRationale = true
            else -> launcher.launch(LOCATION_PERMISSIONS)
        }
    }

    content(requestLocation)

    if (showRationale) {
        PermissionRationaleDialog(
            onContinue = {
                showRationale = false
                launcher.launch(LOCATION_PERMISSIONS)
            },
            onDismiss = { showRationale = false },
        )
    }
    if (showSettings) {
        PermissionSettingsDialog(
            onOpenSettings = {
                showSettings = false
                openAppSettings(context)
            },
            onDismiss = { showSettings = false },
        )
    }
}

/** Explains why location helps, before asking a second time. */
@Composable
fun PermissionRationaleDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.location_rationale_title)) },
        text = { Text(stringResource(R.string.location_rationale_text)) },
        confirmButton = { TextButton(onClick = onContinue) { Text(stringResource(R.string.action_continue)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_not_now)) } },
    )
}

/** Shown when location was denied for good: only the Settings app can change that now. */
@Composable
fun PermissionSettingsDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.location_denied_title)) },
        text = { Text(stringResource(R.string.location_denied_text)) },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_settings)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

private fun isPrecise(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** This app's page in the system Settings, where permissions can be switched on. */
private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Preview
@Composable
private fun PermissionRationaleDialogPreview() {
    MatchItCampusTheme { PermissionRationaleDialog(onContinue = {}, onDismiss = {}) }
}

@Preview
@Composable
private fun PermissionSettingsDialogPreview() {
    MatchItCampusTheme { PermissionSettingsDialog(onOpenSettings = {}, onDismiss = {}) }
}
