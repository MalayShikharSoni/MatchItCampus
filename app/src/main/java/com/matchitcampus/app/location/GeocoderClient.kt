package com.matchitcampus.app.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "MatchIt/GeocoderClient"

/** One result of a place search in the pin picker. */
data class PlaceResult(val label: String, val position: LatLng)

/**
 * Turns coordinates into an address line (reverse geocoding) and a typed place name into
 * coordinates (forward geocoding), with Android's built-in [Geocoder].
 *
 * **Two APIs.** API 33 added listener-based versions of `getFromLocation` / `getFromLocationName`.
 * The older calls block the thread while they go to the network, so they're deprecated; below
 * API 33 they're the only option, so they run on `Dispatchers.IO`. Both paths are wrapped as
 * `suspend` functions, so callers don't see the difference.
 *
 * **It can fail quietly.** Some phones and emulators have no geocoding backend
 * ([Geocoder.isPresent] is false), and results can be empty offline. Every function then
 * returns null / an empty list rather than throwing: an address is nice to have, never required.
 */
class GeocoderClient(context: Context) {

    private val geocoder = Geocoder(context.applicationContext, Locale.getDefault())

    val isAvailable: Boolean get() = Geocoder.isPresent()

    /** A short address line for the point, such as "Block 34, Phagwara", or null. */
    suspend fun addressLine(position: LatLng): String? {
        if (!isAvailable) return null
        val addresses = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(
                        position.latitude,
                        position.longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                            override fun onError(errorMessage: String?) {
                                Log.w(TAG, "Reverse geocoding failed: $errorMessage")
                                cont.resume(emptyList())
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(position.latitude, position.longitude, 1).orEmpty()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            // No network, or the backend is unavailable.
            Log.w(TAG, "Reverse geocoding failed", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid coordinates for reverse geocoding: $position", e)
            emptyList()
        }
        return addresses.firstOrNull()?.toShortLine().also { Log.d(TAG, "Address for $position: $it") }
    }

    /**
     * Places matching [query], best first (at most [maxResults]). Biased to a box around the
     * campus, so "library" finds the campus library rather than one in another city.
     */
    suspend fun search(query: String, maxResults: Int = 5): List<PlaceResult> {
        if (!isAvailable || query.isBlank()) return emptyList()
        val c = CampusMap.CENTER
        val lowLat = c.latitude - SEARCH_BOX_DEGREES
        val lowLng = c.longitude - SEARCH_BOX_DEGREES
        val highLat = c.latitude + SEARCH_BOX_DEGREES
        val highLng = c.longitude + SEARCH_BOX_DEGREES
        val addresses = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(
                        query, maxResults, lowLat, lowLng, highLat, highLng,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses)
                            override fun onError(errorMessage: String?) {
                                Log.w(TAG, "Place search failed: $errorMessage")
                                cont.resume(emptyList())
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, maxResults, lowLat, lowLng, highLat, highLng).orEmpty()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Place search failed for \"$query\"", e)
            emptyList()
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Invalid place search \"$query\"", e)
            emptyList()
        }
        return addresses.map { PlaceResult(it.toShortLine() ?: query, LatLng(it.latitude, it.longitude)) }
            .also { Log.d(TAG, "Place search \"$query\": ${it.size} results") }
    }

    /** The first address line, or the place's name and locality joined, whichever exists. */
    private fun Address.toShortLine(): String? =
        getAddressLine(0)?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(featureName, locality).joinToString(", ").takeIf { it.isNotBlank() }

    private companion object {
        /** About ±11 km around the campus centre. */
        const val SEARCH_BOX_DEGREES = 0.1
    }
}
