package com.matchitcampus.app

import android.app.Application
import android.util.Log
import com.matchitcampus.app.di.AppContainer
import kotlinx.coroutines.launch

private const val TAG = "MatchIt/MatchItApp"

/**
 * The [Application] subclass, registered in AndroidManifest.xml via `android:name`.
 *
 * Android creates exactly one instance of this class before any Activity, which makes it
 * the natural owner of app-wide objects. We use it to build the [AppContainer] (our manual
 * dependency-injection container) once, so every screen shares the same database,
 * repositories and coroutine scope instead of creating its own copies.
 */
class MatchItApp : Application() {

    /** App-wide dependencies. `lateinit` because it can only be built once [onCreate] runs. */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Log.d(TAG, "AppContainer created")
        // Fill the zone table on first run. It's launched in the app-wide scope (on IO) so it
        // doesn't slow down startup, and it isn't cancelled when a screen closes. On later
        // launches it returns straight away, because the table already has rows.
        container.applicationScope.launch { container.zoneRepository.seedIfEmpty() }
        // Sync runs for the whole life of the process; it decides by itself when to listen
        // (signed in and app visible) and when to push (pending changes and online).
        container.syncManager.start()
        // Matching watches Room for items without an embedding (new posts, edits, synced items)
        // and embeds and matches them in the background.
        container.matchRepository.start()
    }
}
