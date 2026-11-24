package com.matchitcampus.app.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Every screen the app can navigate to, as type-safe routes.
 *
 * Navigation Compose (2.8+) accepts `@Serializable` classes as routes instead of strings
 * like `"detail/{id}"`. The compiler then checks the arguments: you cannot navigate to
 * [ItemDetail] without an `itemId`, and you read it back as a typed property with
 * `backStackEntry.toRoute<Routes.ItemDetail>()` instead of parsing a string.
 *
 * Objects are routes with no arguments; data classes carry their arguments as fields.
 */
object Routes {

    // --- Auth graph (shown when signed out) ---
    @Serializable data object Login
    @Serializable data object Register

    // --- Main graph (shown when signed in): top-level tabs ---
    @Serializable data object Home
    @Serializable data object Map
    @Serializable data object MyPosts
    @Serializable data object Claims
    @Serializable data object Profile

    // --- Main graph: screens opened from a tab ---
    /** Post a new item, or edit one of mine when [editItemId] is set. */
    @Serializable data class PostItem(val editItemId: String? = null)
    @Serializable data class ItemDetail(val itemId: String)

    /**
     * Full-screen map to drop a pin (opened from Post item). It opens on the given point, or on
     * the campus centre when there's none yet. Nullable `Double` arguments are supported by
     * type-safe navigation (Navigation 2.10).
     */
    @Serializable data class PinPicker(val latitude: Double? = null, val longitude: Double? = null)

    /** Debug-only: set zone centres from the phone's location (registered in debug builds only). */
    @Serializable data object ZoneCapture

    /** Only registered in debug builds (see [MainNavHost]). */
    @Serializable data object DebugTools
}
