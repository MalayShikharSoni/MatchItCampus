package com.matchitcampus.app.di

import android.content.Context
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.matchitcampus.app.data.local.AppDatabase
import com.matchitcampus.app.data.prefs.SyncPreferences
import com.matchitcampus.app.data.remote.ClaimRemoteDataSource
import com.matchitcampus.app.data.remote.ItemRemoteDataSource
import com.matchitcampus.app.data.remote.UserRemoteDataSource
import com.matchitcampus.app.data.repository.AuthRepository
import com.matchitcampus.app.data.repository.ClaimRepository
import com.matchitcampus.app.data.repository.ItemRepository
import com.matchitcampus.app.data.repository.ZoneRepository
import com.matchitcampus.app.data.sync.ConnectivityObserver
import com.matchitcampus.app.data.sync.SyncManager
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TAG = "MatchIt/AppContainer"

/**
 * Manual dependency injection: one hand-written class that creates and holds the app's
 * long-lived objects.
 *
 * Why manual DI instead of Hilt/Koin/Dagger: every dependency is built with a plain
 * constructor call that you can read top to bottom, with no generated code or annotations.
 * ViewModels get what they need from here through their factories.
 *
 * Phase 2 added the Room database and the local repositories; Phase 3 adds Firebase Auth,
 * Firestore and the sync manager. Phase 4 adds the text embedder. Each is created lazily (`by lazy`) so nothing heavy is built until
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

    /**
     * The one Room database for the whole app. `by lazy` means it's built on first use,
     * not at app start, and the same instance is shared by every repository.
     */
    val database: AppDatabase by lazy { AppDatabase.build(applicationContext) }

    val itemRepository: ItemRepository by lazy { ItemRepository(database.itemDao()) }

    val claimRepository: ClaimRepository by lazy { ClaimRepository(database) }

    val zoneRepository: ZoneRepository by lazy {
        ZoneRepository(
            zoneDao = database.campusZoneDao(),
            readSeedJson = {
                applicationContext.assets.open(ZONES_ASSET).bufferedReader().use { it.readText() }
            },
        )
    }

    // ---------- Firebase (Phase 3) ----------

    /**
     * Firestore with a **memory-only** cache. By default Firestore also keeps its own copy of
     * data on disk. We turn that off so Room is the only thing stored on the phone, which
     * keeps "where does the data live?" easy to answer. Settings must be applied before the
     * first read or write, which `by lazy` guarantees.
     */
    val firestore: FirebaseFirestore by lazy {
        Firebase.firestore.apply {
            firestoreSettings = firestoreSettings {
                setLocalCacheSettings(memoryCacheSettings {})
            }
        }
    }

    val firebaseAuth: FirebaseAuth by lazy { Firebase.auth }

    val syncPreferences: SyncPreferences by lazy { SyncPreferences(applicationContext) }

    val connectivityObserver: ConnectivityObserver by lazy { ConnectivityObserver(applicationContext) }

    val authRepository: AuthRepository by lazy {
        AuthRepository(
            auth = firebaseAuth,
            userRemote = UserRemoteDataSource(firestore),
            userDao = database.userDao(),
            claimRepository = claimRepository,
            externalScope = applicationScope,
        )
    }

    /**
     * True while any of the app's screens is visible. `ProcessLifecycleOwner` follows the
     * whole app rather than a single Activity, so rotating the screen doesn't count as
     * leaving the foreground.
     */
    private val appInForeground: Flow<Boolean> =
        ProcessLifecycleOwner.get().lifecycle.currentStateFlow
            .map { it.isAtLeast(Lifecycle.State.STARTED) }
            .distinctUntilChanged()

    val syncManager: SyncManager by lazy {
        SyncManager(
            authState = authRepository.authState,
            database = database,
            itemRepository = itemRepository,
            claimRepository = claimRepository,
            itemRemote = ItemRemoteDataSource(firestore),
            claimRemote = ClaimRemoteDataSource(firestore),
            syncPreferences = syncPreferences,
            connectivity = connectivityObserver,
            appInForeground = appInForeground,
            scope = applicationScope,
        )
    }

    private companion object {
        const val ZONES_ASSET = "campus_zones.json"
    }
}
