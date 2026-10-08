package com.example.bloomybeauty.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PasswordDigest(val hash: ByteArray, val salt: ByteArray, val iterations: Int)

class PasswordHasher {
    fun hash(password: String): PasswordDigest {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return PasswordDigest(derive(password, salt, ITERATIONS), salt, ITERATIONS)
    }

    fun verify(password: String, digest: PasswordDigest): Boolean =
        MessageDigest.isEqual(digest.hash, derive(password, digest.salt, digest.iterations))

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val characters = password.toCharArray()
        val spec = PBEKeySpec(characters, salt, iterations, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            characters.fill('\u0000')
        }
    }

    private companion object {
        const val ITERATIONS = 210_000
    }
}
