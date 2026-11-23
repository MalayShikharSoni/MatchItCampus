package com.matchitcampus.app.ui.auth

import androidx.annotation.StringRes
import com.matchitcampus.app.R

/**
 * Input checks for the login and registration forms.
 *
 * Each function returns the string resource of the error to show under the field, or null
 * if the value is fine. They are plain functions with no Android framework calls, so they
 * run in fast JVM unit tests (`AuthValidatorTest`).
 *
 * The rules match Firebase's own: it rejects passwords under 6 characters. Checking here
 * first gives instant feedback without a network round trip.
 */
object AuthValidator {

    const val MIN_PASSWORD_LENGTH = 6
    const val MAX_NAME_LENGTH = 50
    const val MAX_REG_NO_LENGTH = 12

    /**
     * Something@something.something with no spaces. Deliberately loose: the real check is
     * Firebase accepting the address. `android.util.Patterns` isn't used because it's
     * unavailable in JVM unit tests.
     */
    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    @StringRes
    fun email(email: String): Int? = when {
        email.isBlank() -> R.string.error_email_required
        !EMAIL_REGEX.matches(email.trim()) -> R.string.error_email_invalid
        else -> null
    }

    /** For login only the presence is checked; the length rule is for new passwords. */
    @StringRes
    fun loginPassword(password: String): Int? =
        if (password.isEmpty()) R.string.error_password_required else null

    @StringRes
    fun newPassword(password: String): Int? = when {
        password.isEmpty() -> R.string.error_password_required
        password.length < MIN_PASSWORD_LENGTH -> R.string.error_password_too_short
        else -> null
    }

    @StringRes
    fun name(name: String): Int? = when {
        name.isBlank() -> R.string.error_name_required
        name.trim().length > MAX_NAME_LENGTH -> R.string.error_name_too_long
        else -> null
    }

    /** The LPU registration number is optional, but if given it must be digits only. */
    @StringRes
    fun regNo(regNo: String): Int? {
        val value = regNo.trim()
        return when {
            value.isEmpty() -> null
            !value.all { it.isDigit() } || value.length > MAX_REG_NO_LENGTH -> R.string.error_reg_no_invalid
            else -> null
        }
    }
}
