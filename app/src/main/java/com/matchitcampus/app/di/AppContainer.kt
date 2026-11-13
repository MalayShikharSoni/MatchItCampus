package com.matchitcampus.app.di

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private const val TAG = "MatchIt/AppContainer"

/**
 * Manual dependency injection: one hand-written class that creates and holds the app's
 * long-lived objects.
 *
 * Why manual DI instead of Hilt/Koin/Dagger: every dependency is built with a plain
 * constructor call that you can read top to bottom, with no generated code or annotations.
 * ViewModels get what they need from here through their factories.
 *
 * Later phases add the Room database, Firestore, repositories, the sync manager and the
 * text embedder here. Each is created lazily (`by lazy`) so nothing heavy is built until
 * something actually uses it.
 *
 * @param context any context; only the application context is kept, so an Activity can
 *   never leak through this long-lived object.
 */
class AppContainer(context: Context) {

    val applicationContext: Context = context.applicationContext

    /**
     * Logs any exception that escapes a coroutine launched in [applicationScope], instead of
     * letting it crash the app. Errors are still visible in Logcat under `tag:MatchIt`.
     */
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Uncaught exception in applicationScope", throwable)
    }

    /**
     * A coroutine scope that lives as long as the app process, for work that must not be
     * cancelled when a screen closes (for example pushing pending changes to Firestore).
     *
     * - [SupervisorJob]: one failed child job does not cancel its siblings or the scope.
     * - [Dispatchers.IO]: the default for database and network work.
     * - [exceptionHandler]: logs failures that nobody else catches.
     *
     * Screen-level work should use `viewModelScope` instead, so it is cancelled with the screen.
     */
    val applicationScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
}
