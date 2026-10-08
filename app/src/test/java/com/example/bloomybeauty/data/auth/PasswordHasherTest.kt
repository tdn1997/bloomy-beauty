package com.example.bloomybeauty.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    private val hasher = PasswordHasher()

    @Test
    fun correctPasswordWorksAndWrongPasswordFails() {
        val digest = hasher.hash("password123")
        assertTrue(hasher.verify("password123", digest))
        assertFalse(hasher.verify("password124", digest))
        assertFalse(digest.hash.contentEquals("password123".toByteArray()))
    }

    @Test
    fun identicalPasswordsHaveDifferentSaltsAndHashes() {
        val first = hasher.hash("password123")
        val second = hasher.hash("password123")
        assertFalse(first.salt.contentEquals(second.salt))
        assertFalse(first.hash.contentEquals(second.hash))
        assertTrue(hasher.verify("password123", first))
        assertTrue(hasher.verify("password123", second))
    }

    @Test
    fun whitespaceAndUnicodeRemainPartOfPassword() {
        val digest = hasher.hash(" Đẹp mỗi ngày ")
        assertTrue(hasher.verify(" Đẹp mỗi ngày ", digest))
        assertFalse(hasher.verify("Đẹp mỗi ngày", digest))
    }
}
