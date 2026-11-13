package com.matchitcampus.app

import android.app.Application
import android.util.Log
import com.matchitcampus.app.di.AppContainer

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
    }
}
