package com.example.bloomybeauty.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthValidationTest {
    @Test
    fun emailIsTrimmedAndCaseInsensitive() {
        assertEquals("customer@example.com", AuthValidation.normalizeEmail("  Customer@Example.COM  "))
        assertNull(AuthValidation.validate("  Customer@Example.COM  ", "password123"))
    }

    @Test
    fun malformedEmailsAreRejected() {
        listOf("", "customer", "a@", "a@localhost", "a b@example.com", "a..b@example.com", ".a@example.com", "a@-example.com").forEach {
            assertEquals(AuthError.INVALID_EMAIL, AuthValidation.validate(it, "password123"))
        }
    }

    @Test
    fun passwordLengthAndBlankPasswordsAreRejected() {
        listOf("", "1234567", "        ", "x".repeat(129)).forEach {
            assertEquals(AuthError.INVALID_PASSWORD, AuthValidation.validate("a@example.com", it))
        }
    }

    @Test
    fun registrationRequiresNameAndMatchingConfirmation() {
        assertEquals(AuthError.INVALID_NAME, AuthValidation.validate("a@example.com", "password123", " ", "password123"))
        assertEquals(AuthError.PASSWORD_MISMATCH, AuthValidation.validate("a@example.com", "password123", "Khách hàng", "password124"))
        assertNull(AuthValidation.validate("a@example.com", "password123", "Khách hàng", "password123"))
    }

    @Test
    fun passwordWhitespaceIsPreserved() {
        assertNull(AuthValidation.validate("a@example.com", " password ", "Khách hàng", " password "))
        assertEquals(AuthError.PASSWORD_MISMATCH, AuthValidation.validate("a@example.com", " password ", "Khách hàng", "password"))
    }
}
