package com.matchitcampus.app.ui.auth

import com.matchitcampus.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidatorTest {

    @Test
    fun email_blank_isRequired() {
        assertEquals(R.string.error_email_required, AuthValidator.email("   "))
    }

    @Test
    fun email_withoutAtOrDot_isInvalid() {
        assertEquals(R.string.error_email_invalid, AuthValidator.email("riya.example.com"))
        assertEquals(R.string.error_email_invalid, AuthValidator.email("riya@example"))
        assertEquals(R.string.error_email_invalid, AuthValidator.email("ri ya@example.com"))
    }

    @Test
    fun email_valid_surroundingSpacesAllowed() {
        assertNull(AuthValidator.email("riya@lpu.in"))
        assertNull(AuthValidator.email("  riya.sharma@example.co.in  "))
    }

    @Test
    fun loginPassword_onlyChecksPresence() {
        assertEquals(R.string.error_password_required, AuthValidator.loginPassword(""))
        // Short passwords are allowed at login: the server decides if they're right.
        assertNull(AuthValidator.loginPassword("abc"))
    }

    @Test
    fun newPassword_needsSixCharacters() {
        assertEquals(R.string.error_password_required, AuthValidator.newPassword(""))
        assertEquals(R.string.error_password_too_short, AuthValidator.newPassword("12345"))
        assertNull(AuthValidator.newPassword("123456"))
    }

    @Test
    fun name_blankOrTooLong_isRejected() {
        assertEquals(R.string.error_name_required, AuthValidator.name(" "))
        assertEquals(R.string.error_name_too_long, AuthValidator.name("a".repeat(51)))
        assertNull(AuthValidator.name("Aman Verma"))
    }

    @Test
    fun regNo_isOptionalButDigitsOnly() {
        assertNull(AuthValidator.regNo(""))
        assertNull(AuthValidator.regNo("12345678"))
        assertEquals(R.string.error_reg_no_invalid, AuthValidator.regNo("12AB5678"))
        assertEquals(R.string.error_reg_no_invalid, AuthValidator.regNo("1234567890123"))
    }
}
