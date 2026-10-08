package com.example.bloomybeauty.data.auth

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRepositoryTest {
    private lateinit var context: Context
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DATABASE)
        helper = AuthDatabaseHelper(context, TEST_DATABASE)
        repository = AuthRepository(helper)
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun accountAndSessionSurviveDatabaseReopen() = runBlocking {
        val created = register()
        assertEquals("customer@example.com", created.user.email)
        helper.close()
        helper = AuthDatabaseHelper(context, TEST_DATABASE)
        repository = AuthRepository(helper)
        assertEquals(created.user, repository.restoreSession())
        repository.logout()
        assertNull(repository.restoreSession())
        assertEquals(created.user, success(repository.login(" CUSTOMER@EXAMPLE.COM ", PASSWORD)).user)
    }

    @Test
    fun duplicateEmailDoesNotOverwriteAccountOrSession() = runBlocking {
        val created = register()
        assertEquals(
            AuthResult.Failure(AuthError.DUPLICATE_EMAIL),
            repository.register("Người khác", " CUSTOMER@EXAMPLE.COM ", "different123", "different123"),
        )
        assertEquals(created.user, repository.restoreSession())
        repository.logout()
        assertEquals(created.user, success(repository.login(EMAIL, PASSWORD)).user)
    }

    @Test
    fun incorrectCredentialsDoNotCreateSession() = runBlocking {
        register()
        repository.logout()
        assertEquals(AuthResult.Failure(AuthError.INVALID_CREDENTIALS), repository.login(EMAIL, "incorrect123"))
        assertEquals(AuthResult.Failure(AuthError.INVALID_CREDENTIALS), repository.login("missing@example.com", PASSWORD))
        assertNull(repository.restoreSession())
    }

    @Test
    fun queryParametersDoNotAllowSqlInjection() = runBlocking {
        register()
        repository.logout()
        // Valid characters in the local part still have to match one exact email.
        assertEquals(
            AuthResult.Failure(AuthError.INVALID_CREDENTIALS),
            repository.login("x'OR'1'='1@example.com", PASSWORD),
        )
        assertNull(repository.restoreSession())
    }

    @Test
    fun passwordsAreStoredAsIndependentSaltedHashes() = runBlocking {
        register()
        repository.register("Khách thứ hai", "second@example.com", PASSWORD, PASSWORD)
        helper.readableDatabase.query(AuthContract.Users.TABLE, null, null, null, null, null, null).use { cursor ->
            assertEquals(2, cursor.count)
            assertEquals(-1, cursor.getColumnIndex("password"))
            assertTrue(cursor.moveToFirst())
            val firstHash = cursor.getBlob(cursor.getColumnIndexOrThrow(AuthContract.Users.PASSWORD_HASH))
            val firstSalt = cursor.getBlob(cursor.getColumnIndexOrThrow(AuthContract.Users.PASSWORD_SALT))
            assertFalse(firstHash.contentEquals(PASSWORD.toByteArray()))
            assertTrue(cursor.moveToNext())
            assertFalse(firstHash.contentEquals(cursor.getBlob(cursor.getColumnIndexOrThrow(AuthContract.Users.PASSWORD_HASH))))
            assertFalse(firstSalt.contentEquals(cursor.getBlob(cursor.getColumnIndexOrThrow(AuthContract.Users.PASSWORD_SALT))))
        }
    }

    @Test
    fun logoutAndAccountSwitchRestoreOnlyCurrentUser() = runBlocking {
        val first = register()
        repository.logout()
        assertNull(repository.restoreSession())
        val second = success(repository.register("Khách thứ hai", "second@example.com", PASSWORD, PASSWORD))
        assertEquals(second.user, repository.restoreSession())
        repository.logout()
        repository.login(EMAIL, PASSWORD)
        assertEquals(first.user, repository.restoreSession())
    }

    @Test
    fun concurrentRegistrationsProduceOneAccount() = runBlocking {
        val results = listOf(async { repository.register("Khách A", EMAIL, PASSWORD, PASSWORD) },
            async { repository.register("Khách B", EMAIL, PASSWORD, PASSWORD) }).awaitAll()
        assertEquals(1, results.count { it is AuthResult.Success })
        assertEquals(1, results.count { it == AuthResult.Failure(AuthError.DUPLICATE_EMAIL) })
        assertNotNull(repository.restoreSession())
    }

    @Test
    fun invalidRegistrationDoesNotPersistAccount() = runBlocking {
        assertEquals(
            AuthResult.Failure(AuthError.PASSWORD_MISMATCH),
            repository.register("Khách hàng", EMAIL, PASSWORD, "different123"),
        )
        assertNull(repository.restoreSession())
        assertEquals(AuthResult.Failure(AuthError.INVALID_CREDENTIALS), repository.login(EMAIL, PASSWORD))
    }

    private suspend fun register() = success(repository.register("Khách hàng", EMAIL, PASSWORD, PASSWORD))

    private fun success(result: AuthResult): AuthResult.Success {
        assertTrue("Expected success, received $result", result is AuthResult.Success)
        return result as AuthResult.Success
    }

    private companion object {
        const val TEST_DATABASE = "auth_repository_test.db"
        const val EMAIL = "customer@example.com"
        const val PASSWORD = "password123"
    }
}
