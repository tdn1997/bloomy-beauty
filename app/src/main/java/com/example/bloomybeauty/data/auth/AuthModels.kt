package com.example.bloomybeauty.data.auth

import java.util.Locale

enum class UserRole { CUSTOMER, ADMIN }

data class AuthUser(val id: Long, val name: String, val email: String, val role: UserRole = UserRole.CUSTOMER)

enum class AuthError {
    INVALID_NAME, INVALID_EMAIL, INVALID_PASSWORD, PASSWORD_MISMATCH,
    DUPLICATE_EMAIL, INVALID_CREDENTIALS, STORAGE,
}

sealed interface AuthResult {
    data class Success(val user: AuthUser) : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}

object AuthValidation {
    private val emailPattern = Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$")

    fun normalizeEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

    private fun validEmail(email: String): Boolean {
        val normalized = normalizeEmail(email)
        if (normalized.length > 254 || !emailPattern.matches(normalized)) return false
        val local = normalized.substringBefore('@')
        val domain = normalized.substringAfter('@')
        return local.length <= 64 && !local.startsWith('.') && !local.endsWith('.') && !local.contains("..") &&
            domain.split('.').all { it.length <= 63 && !it.startsWith('-') && !it.endsWith('-') }
    }

    fun validate(
        email: String,
        password: String,
        name: String? = null,
        confirmation: String? = null,
    ): AuthError? = when {
        name != null && name.trim().length !in 2..80 -> AuthError.INVALID_NAME
        !validEmail(email) -> AuthError.INVALID_EMAIL
        password.length !in 8..128 || password.isBlank() -> AuthError.INVALID_PASSWORD
        confirmation != null && password != confirmation -> AuthError.PASSWORD_MISMATCH
        else -> null
    }
}
