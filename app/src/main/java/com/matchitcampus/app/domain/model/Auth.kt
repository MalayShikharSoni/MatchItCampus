package com.matchitcampus.app.domain.model

/**
 * The signed-in account, as the rest of the app sees it. It's a plain Kotlin class, so
 * screens and ViewModels never depend on Firebase types.
 *
 * The display name isn't here: it lives in the cached profile ([UserProfile]), which is
 * filled from Firestore and stays available offline.
 */
data class AuthUser(
    val uid: String,
    val email: String,
)

/** The signed-in user's profile (`users/{uid}` in Firestore, `users` table in Room). */
data class UserProfile(
    val uid: String,
    val name: String,
    val email: String,
    val regNo: String?,
)

/**
 * Why sign-in or registration failed, in terms the UI can explain to a person.
 *
 * `AuthRepository` maps Firebase's many exception types onto these few cases, and the
 * ViewModel maps each case to a friendly message.
 */
enum class AuthFailure {
    /** Wrong email or password. Firebase deliberately doesn't say which one. */
    WRONG_CREDENTIALS,
    INVALID_EMAIL,
    EMAIL_IN_USE,
    WEAK_PASSWORD,
    ACCOUNT_DISABLED,
    TOO_MANY_ATTEMPTS,
    /** No connection, or Firebase didn't answer in time. */
    NETWORK,
    UNKNOWN,
}

/** The result of a sign-in or registration attempt. */
sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val reason: AuthFailure) : AuthResult
}
