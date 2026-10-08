package com.example.bloomybeauty.data.catalog

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.AuthContract
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.AuthRepository
import com.example.bloomybeauty.data.auth.AuthResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatalogRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        helper = AuthDatabaseHelper(context, TEST_DATABASE)
    }

    @After
    fun tearDown() {
        helper.close()
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun favoritesPersistAndBelongToOneAccount() = runBlocking {
        val auth = AuthRepository(helper)
        val first = auth.register("Khách thứ nhất", "first@example.com", PASSWORD, PASSWORD) as AuthResult.Success
        val second = auth.register("Khách thứ hai", "second@example.com", PASSWORD, PASSWORD) as AuthResult.Success
        val catalog = CatalogRepository(helper)
        auth.login("first@example.com",PASSWORD)
        assertEquals(8, catalog.load(first.user.id).products.size)
        assertEquals(4, catalog.load(first.user.id).categories.size)
        catalog.toggleFavorite(first.user.id, "cleanser")
        assertEquals(setOf("cleanser"), catalog.load(first.user.id).favoriteIds)
        auth.login("second@example.com",PASSWORD)
        assertTrue(catalog.load(second.user.id).favoriteIds.isEmpty())
        auth.login("first@example.com",PASSWORD)
        helper.close()
        helper = AuthDatabaseHelper(context, TEST_DATABASE)
        val reopened = CatalogRepository(helper)
        assertEquals(setOf("cleanser"), reopened.load(first.user.id).favoriteIds)
        reopened.toggleFavorite(first.user.id, "cleanser")
        assertTrue(reopened.load(first.user.id).favoriteIds.isEmpty())
    }

    @Test
    fun migrationFromVersionOnePreservesAccountAndSession() = runBlocking {
        helper.close()
        val path = context.getDatabasePath(TEST_DATABASE)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE users (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE, password_hash BLOB NOT NULL, password_salt BLOB NOT NULL, password_iterations INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE auth_session (_id INTEGER PRIMARY KEY CHECK (_id = 1), user_id INTEGER NOT NULL REFERENCES users(_id) ON DELETE CASCADE)")
            val digest = com.example.bloomybeauty.data.auth.PasswordHasher().hash(PASSWORD)
            old.execSQL("INSERT INTO users VALUES (?, ?, ?, ?, ?, ?)", arrayOf(1, "Nguyễn Thị Mỹ", "existing@example.com", digest.hash, digest.salt, digest.iterations))
            old.execSQL("INSERT INTO auth_session VALUES (1, 1)")
            old.version = 1
        }
        helper = AuthDatabaseHelper(context, TEST_DATABASE)
        val auth = AuthRepository(helper)
        assertEquals("Nguyễn Thị Mỹ", auth.restoreSession()?.name)
        val catalog = CatalogRepository(helper)
        assertEquals(8, catalog.load(1).products.size)
        assertEquals(AuthContract.DATABASE_VERSION, helper.readableDatabase.version)
        auth.logout()
        assertTrue(auth.login("existing@example.com", PASSWORD) is AuthResult.Success)
    }

    @Test
    fun bundledPhotosExistForEverySeededProduct() = runBlocking {
        val user=(AuthRepository(helper).register("Khách xem ảnh","photos@example.com",PASSWORD,PASSWORD) as AuthResult.Success).user
        val snapshot = CatalogRepository(helper).load(user.id)
        snapshot.products.forEach { product ->
            val id = context.resources.getIdentifier(product.imageKey, "drawable", context.packageName)
            assertFalse("Missing ${product.imageKey}", id == 0)
            assertNotNull(context.resources.getDrawable(id, context.theme))
        }
    }

    private companion object {
        const val TEST_DATABASE = "catalog_repository_test.db"
        const val PASSWORD = "password123"
    }
}
