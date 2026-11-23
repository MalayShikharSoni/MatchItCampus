package com.matchitcampus.app.data.repository

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.matchitcampus.app.data.local.dao.UserDao
import com.matchitcampus.app.data.remote.UserRemoteDataSource
import com.matchitcampus.app.data.remote.dto.UserDto
import com.matchitcampus.app.data.remote.mapper.toEntity
import com.matchitcampus.app.domain.model.AuthFailure
import com.matchitcampus.app.domain.model.AuthResult
import com.matchitcampus.app.domain.model.AuthUser
import com.matchitcampus.app.domain.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MatchIt/AuthRepository"

/**
 * Sign-in, registration and logout, on top of Firebase Auth (email + password).
 *
 * The rest of the app never sees Firebase types: it gets [AuthUser], [UserProfile] and
 * [AuthResult]. Firebase's exceptions are turned into a few [AuthFailure] cases that the
 * UI can explain.
 *
 * @param externalScope the app-wide scope. Sign-in and registration run in it (see [runInAppScope]).
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val userRemote: UserRemoteDataSource,
    private val userDao: UserDao,
    private val claimRepository: ClaimRepository,
    private val externalScope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * The signed-in account, or null when signed out.
     *
     * Firebase reports sign-in changes through an `AuthStateListener` callback. `callbackFlow`
     * turns that into a Flow (the listener is removed in `awaitClose`), and `stateIn` makes it
     * a StateFlow that always holds the latest value. Its starting value is read synchronously
     * from `auth.currentUser`, so on app start we already know whether to show the login
     * screen, with no loading flash.
     */
    val authState: StateFlow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.toAuthUser())
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.stateIn(externalScope, SharingStarted.Eagerly, auth.currentUser?.toAuthUser())

    /** The cached profile (name, email, reg. no.) of a user, from Room. */
    fun observeProfile(uid: String): Flow<UserProfile?> =
        userDao.observe(uid).map { entity ->
            entity?.let { UserProfile(it.uid, it.name, it.email, it.regNo) }
        }

    suspend fun signIn(email: String, password: String): AuthResult = runInAppScope {
        val user = auth.signInWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Sign-in returned no user")
        Log.d(TAG, "Signed in ${user.uid}")
        cacheProfile(user)
    }

    /**
     * Creates the account, then the `users/{uid}` profile document.
     *
     * The profile is cached in Room first, so the app can show the name even if the
     * Firestore write fails. If it does fail, the next sign-in recreates the document.
     */
    suspend fun register(name: String, email: String, password: String, regNo: String?): AuthResult =
        runInAppScope {
            val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
                ?: error("Registration returned no user")
            Log.d(TAG, "Registered ${user.uid}")
            // Also store the name on the Firebase account itself, as a fallback.
            user.updateProfile(userProfileChangeRequest { displayName = name.trim() }).await()
            val profile = UserDto(
                name = name.trim(),
                email = email.trim(),
                regNo = regNo?.trim()?.ifEmpty { null },
                createdAt = clock(),
            )
            userDao.upsert(profile.toEntity(user.uid))
            saveProfileRemotely(user.uid, profile)
        }

    /**
     * Signs out and removes private data from this phone: claims and the cached profile.
     * Public items stay, so the next person to sign in on this phone has a warm feed.
     *
     * `SyncManager` sees [authState] become null and stops its Firestore listeners.
     *
     * It runs in [externalScope] because signing out closes the Profile screen, which would
     * cancel a ViewModel coroutine before the cleanup finished. It also waits for
     * [authState] to report null before clearing: from then on, `SyncManager` refuses to
     * write claims, so a late Firestore snapshot can't put them back.
     */
    suspend fun signOut() {
        externalScope.async {
            auth.signOut()
            authState.first { it == null }
            claimRepository.clearAll()
            userDao.deleteAll()
            Log.d(TAG, "Signed out and cleared claims and profile")
        }.await()
    }

    /**
     * Runs [block] in [externalScope] and waits for it.
     *
     * Why: as soon as sign-in succeeds, [authState] changes and the app swaps the login
     * screen for the main screens. That destroys the login ViewModel and cancels its
     * coroutines. If the profile were saved in the ViewModel's coroutine, it could be cut off
     * halfway. `externalScope.async { }` runs the work in the app-wide scope, so it finishes
     * even if the caller is cancelled. The caller just stops waiting for the result.
     *
     * Every failure becomes an [AuthResult.Failure], and the whole operation has a timeout,
     * so a hanging network call becomes a "check your connection" message instead of a
     * spinner that never stops.
     */
    private suspend fun runInAppScope(block: suspend () -> Unit): AuthResult =
        externalScope.async {
            try {
                withTimeoutOrNull(AUTH_TIMEOUT_MS) {
                    block()
                    AuthResult.Success
                } ?: AuthResult.Failure(AuthFailure.NETWORK).also { Log.w(TAG, "Auth timed out") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Auth failed", e)
                AuthResult.Failure(e.toAuthFailure())
            }
        }.await()

    /**
     * After sign-in, copies `users/{uid}` into Room. If the document is missing (for example
     * the write failed at registration), it's recreated from the account's name and email.
     * Profile problems never fail the sign-in itself: the user is already signed in.
     */
    private suspend fun cacheProfile(user: FirebaseUser) {
        // Success(null) means "the document doesn't exist"; failure means "we couldn't tell"
        // (error or timeout). Only the first case should recreate the document.
        val remote: Result<UserDto?> = try {
            withTimeoutOrNull(PROFILE_TIMEOUT_MS) { Result.success(userRemote.get(user.uid)) }
                ?: Result.failure(IllegalStateException("Profile read timed out"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't load profile for ${user.uid}", e)
            Result.failure(e)
        }

        val profile = remote.getOrNull()
        when {
            profile != null -> userDao.upsert(profile.toEntity(user.uid))
            // The read worked and the document doesn't exist: recreate it.
            remote.isSuccess -> {
                val rebuilt = fallbackProfile(user)
                userDao.upsert(rebuilt.toEntity(user.uid))
                saveProfileRemotely(user.uid, rebuilt)
            }
            // The read failed: keep any cached profile, otherwise cache the fallback locally.
            userDao.getById(user.uid) == null -> userDao.upsert(fallbackProfile(user).toEntity(user.uid))
        }
    }

    private fun fallbackProfile(user: FirebaseUser) = UserDto(
        name = user.displayName?.takeIf { it.isNotBlank() } ?: user.email.orEmpty().substringBefore('@'),
        email = user.email.orEmpty(),
        regNo = null,
        createdAt = clock(),
    )

    private suspend fun saveProfileRemotely(uid: String, profile: UserDto) {
        try {
            withTimeoutOrNull(PROFILE_TIMEOUT_MS) { userRemote.save(uid, profile) }
                ?: Log.w(TAG, "Saving profile for $uid timed out")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't save profile for $uid", e)
        }
    }

    private fun FirebaseUser.toAuthUser() = AuthUser(uid = uid, email = email.orEmpty())

    private companion object {
        const val AUTH_TIMEOUT_MS = 20_000L
        const val PROFILE_TIMEOUT_MS = 10_000L
    }
}

/**
 * Maps Firebase's exceptions to [AuthFailure]. The error codes are Firebase's own strings.
 * New projects have "email enumeration protection" on, so a wrong password and an unknown
 * email both arrive as an invalid credential: the app can't (and shouldn't) tell them apart.
 */
private fun Exception.toAuthFailure(): AuthFailure = when (this) {
    is FirebaseAuthWeakPasswordException -> AuthFailure.WEAK_PASSWORD
    is FirebaseAuthUserCollisionException -> AuthFailure.EMAIL_IN_USE
    is FirebaseAuthInvalidCredentialsException ->
        if (errorCode == "ERROR_INVALID_EMAIL") AuthFailure.INVALID_EMAIL else AuthFailure.WRONG_CREDENTIALS
    is FirebaseAuthInvalidUserException ->
        if (errorCode == "ERROR_USER_DISABLED") AuthFailure.ACCOUNT_DISABLED else AuthFailure.WRONG_CREDENTIALS
    is FirebaseTooManyRequestsException -> AuthFailure.TOO_MANY_ATTEMPTS
    is FirebaseNetworkException -> AuthFailure.NETWORK
    else -> AuthFailure.UNKNOWN
}
