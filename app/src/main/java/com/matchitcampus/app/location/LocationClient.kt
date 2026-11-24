package com.matchitcampus.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MatchIt/LocationClient"

/**
 * The phone's location, through Google Play services' **Fused Location Provider**.
 *
 * "Fused" means Play services combines GPS, Wi-Fi and cell towers and picks whatever gives the
 * requested accuracy for the least battery, so the app doesn't manage those itself.
 *
 * This is the only class that imports Play services location. It never asks for permission
 * itself (that needs an Activity; see `LocationPermissionGate`): every function first checks
 * [hasPermission] and returns null / an empty flow without it.
 *
 * **Approximate only.** On Android 12+ the user can grant coarse location only. Everything here
 * still works then; the fix is just about 1–3 km off, which [isPrecise] lets the UI mention.
 */
class LocationClient(private val context: Context) {

    private val fused = LocationServices.getFusedLocationProviderClient(context)

    /** True if either fine or coarse location has been granted. */
    fun hasPermission(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
        isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** True only with fine (GPS-level) location. */
    fun isPrecise(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    /**
     * One fresh location fix, or null if there's no permission, location is switched off, or
     * no fix arrives within [timeoutMs].
     *
     * `getCurrentLocation` asks for a *new* fix (unlike `lastLocation`, which may be hours old).
     * High accuracy with fine permission, balanced with coarse only. `withTimeoutOrNull` stops
     * waiting after [timeoutMs], so the UI never spins forever indoors.
     */
    @SuppressLint("MissingPermission") // Checked by hasPermission() first.
    suspend fun currentLocation(timeoutMs: Long = 10_000): LatLng? {
        if (!hasPermission()) return null
        val request = CurrentLocationRequest.Builder()
            .setPriority(if (isPrecise()) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setDurationMillis(timeoutMs)
            .build()
        return try {
            withTimeoutOrNull(timeoutMs) {
                fused.getCurrentLocation(request, null).await()?.toLatLng()
            }.also { Log.d(TAG, "Current location: $it") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // SecurityException (permission revoked meanwhile) or a Play services error.
            Log.e(TAG, "Couldn't get the current location", e)
            null
        }
    }

    /**
     * Live location updates while collected (used by the Map screen's "my location" dot).
     *
     * `callbackFlow` turns Play services' callback API into a Flow: the callback `trySend`s each
     * fix into the flow, and `awaitClose` runs when the collector stops (screen left, app in the
     * background), where the updates are removed so the GPS can switch off again.
     */
    @SuppressLint("MissingPermission") // Checked by hasPermission() first.
    fun locationUpdates(intervalMs: Long = 5_000): Flow<Location> = callbackFlow {
        if (!hasPermission()) {
            Log.w(TAG, "Location updates requested without permission")
            close()
            return@callbackFlow
        }
        val request = LocationRequest.Builder(
            if (isPrecise()) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            intervalMs,
        ).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper()).await()
            Log.d(TAG, "Location updates started")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't start location updates", e)
            close(e)
        }
        awaitClose {
            fused.removeLocationUpdates(callback)
            Log.d(TAG, "Location updates stopped")
        }
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

fun Location.toLatLng() = LatLng(latitude, longitude)
