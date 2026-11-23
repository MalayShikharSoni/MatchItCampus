package com.matchitcampus.app.ui.auth

import androidx.annotation.StringRes
import com.matchitcampus.app.R
import com.matchitcampus.app.domain.model.AuthFailure

/** The friendly message shown for each way sign-in or registration can fail. */
@StringRes
fun AuthFailure.messageRes(): Int = when (this) {
    AuthFailure.WRONG_CREDENTIALS -> R.string.auth_error_wrong_credentials
    AuthFailure.INVALID_EMAIL -> R.string.auth_error_invalid_email
    AuthFailure.EMAIL_IN_USE -> R.string.auth_error_email_in_use
    AuthFailure.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthFailure.ACCOUNT_DISABLED -> R.string.auth_error_disabled
    AuthFailure.TOO_MANY_ATTEMPTS -> R.string.auth_error_too_many
    AuthFailure.NETWORK -> R.string.auth_error_network
    AuthFailure.UNKNOWN -> R.string.auth_error_unknown
}
