package com.example.bloomybeauty.data.cart

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.catalog.CatalogSchema
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CartRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var repository: CartRepository
    private var userId = 0L
    private var otherId = 0L

    @Before
    fun setUp() = runBlocking {
        context.deleteDatabase(DATABASE)
        helper = AuthDatabaseHelper(context, DATABASE)
        repository = CartRepository(helper)
        val auth = AuthRepository(helper)
        userId = (auth.register("Nguyễn Thị Mỹ", "cart@example.com", PASSWORD, PASSWORD) as AuthResult.Success).user.id
        otherId = (auth.register("Trần Minh", "othercart@example.com", PASSWORD, PASSWORD) as AuthResult.Success).user.id
        auth.login("cart@example.com",PASSWORD)
        Unit
    }

    @After
    fun tearDown() { helper.close(); context.deleteDatabase(DATABASE) }

    @Test
    fun quantityTotalsCartAndShippingPersistAndStayAccountScoped() = runBlocking {
        repository.add(userId, "cleanser")
        repository.add(userId, "cleanser")
        repository.add(userId, "lip_balm")
        assertEquals(416_000L, repository.load(userId).totalVnd)
        repository.setQuantity(userId, "cleanser", 3)
        assertEquals(608_000L, repository.load(userId).totalVnd)
        val saved = repository.saveShipping(userId, ShippingDetails(" Nguyễn Thị Mỹ ", "+84 912 345 678", "12 Nguyễn Văn A, TP Hồ Chí Minh"))
        assertEquals("0912345678", saved.shipping.phone)
        helper.close()
        helper = AuthDatabaseHelper(context, DATABASE)
        repository = CartRepository(helper)
        assertEquals(4, repository.load(userId).quantity)
        assertEquals("Nguyễn Thị Mỹ", repository.load(userId).shipping.recipient)
        AuthRepository(helper).login("othercart@example.com",PASSWORD)
        assertTrue(repository.load(otherId).items.isEmpty())
        assertEquals(ShippingDetails(), repository.load(otherId).shipping)
        repository.remove(otherId, "cleanser")
        AuthRepository(helper).login("cart@example.com",PASSWORD)
        assertEquals(4, repository.load(userId).quantity)
        repository.remove(userId, "lip_balm")
        assertEquals(576_000L, repository.load(userId).totalVnd)
        repository.remove(userId, "cleanser")
        expectCart(CartError.EMPTY) { repository.validateForShipping(userId) }
    }

    @Test
    fun hiddenSoldOutAndInvalidQuantitiesCannotBeAdded() = runBlocking {
        helper.writableDatabase.execSQL("UPDATE products SET active = 0 WHERE id = 'cleanser'")
        helper.writableDatabase.execSQL("UPDATE products SET stock = 0 WHERE id = 'lip_balm'")
        expectCart(CartError.UNAVAILABLE) { repository.add(userId, "cleanser") }
        expectCart(CartError.STOCK) { repository.add(userId, "lip_balm") }
        expectCart(CartError.UNAVAILABLE) { repository.add(userId, "missing") }
        repository.add(userId, "micellar")
        for (quantity in listOf(0, -1, 100)) expectCart(CartError.QUANTITY) { repository.setQuantity(userId, "micellar", quantity) }
        expectCart(CartError.STOCK) { repository.setQuantity(userId, "micellar", 31) }
        assertEquals(1, repository.load(userId).quantity)
        assertFalse(CatalogRepository(helper).load(userId).products.any { it.id == "cleanser" })
    }

    @Test
    fun priceChangesRequireExplicitAcceptanceAndDoNotAddQuantity() = runBlocking {
        repository.add(userId, "cleanser")
        helper.writableDatabase.execSQL("UPDATE products SET price_vnd = 200000 WHERE id = 'cleanser'")
        assertTrue(repository.load(userId).items.single().priceChanged)
        expectCart(CartError.PRICE_CHANGED) { repository.add(userId, "cleanser") }
        expectCart(CartError.PRICE_CHANGED) { repository.validateForShipping(userId) }
        assertEquals(1, repository.load(userId).quantity)
        expectCart(CartError.PRICE_CHANGED) { repository.acceptCurrentPrices(userId, mapOf("cleanser" to 192_000L)) }
        assertTrue(repository.load(userId).items.single().priceChanged)
        repository.acceptCurrentPrices(userId, mapOf("cleanser" to 200_000L))
        assertEquals(200_000L, repository.validateForShipping(userId).totalVnd)
        assertFalse(repository.load(userId).items.single().priceChanged)
    }

    @Test
    fun shippingRechecksStockAndStatusAndDoesNotOverwriteSavedAddressOnFailure() = runBlocking {
        repository.add(userId, "cleanser")
        repository.setQuantity(userId, "cleanser", 2)
        repository.saveShipping(userId, VALID_SHIPPING)
        helper.writableDatabase.execSQL("UPDATE products SET stock = 1 WHERE id = 'cleanser'")
        expectCart(CartError.STOCK) { repository.saveShipping(userId, VALID_SHIPPING.copy(address = "Địa chỉ mới đủ dài")) }
        assertEquals(VALID_SHIPPING.address, repository.load(userId).shipping.address)
        repository.setQuantity(userId, "cleanser", 1)
        helper.writableDatabase.execSQL("UPDATE products SET active = 0 WHERE id = 'cleanser'")
        expectCart(CartError.UNAVAILABLE) { repository.validateForShipping(userId) }
        assertEquals(1, repository.load(userId).quantity)
        repository.remove(userId, "cleanser")
        assertTrue(repository.load(userId).items.isEmpty())
    }

    @Test
    fun invalidShippingCannotBePersisted() = runBlocking {
        repository.add(userId, "cleanser")
        try {
            repository.saveShipping(userId, VALID_SHIPPING.copy(phone = "123"))
            fail("Invalid shipping accepted")
        } catch (exception: ShippingException) { assertEquals(ShippingError.PHONE, exception.error) }
        assertEquals(ShippingDetails(), repository.load(userId).shipping)
    }

    @Test
    fun concurrentAddsDoNotLoseIncrementsOrReduceStock() = runBlocking {
        coroutineScope { (1..8).map { async(Dispatchers.IO) { repository.add(userId, "cleanser") } }.awaitAll() }
        val item = repository.load(userId).items.single()
        assertEquals(8, item.quantity)
        assertEquals(30, item.product.stock)
    }

    @Test
    fun versionTwoMigrationKeepsSessionFavoritesAndProductEdits() = runBlocking {
        helper.close()
        context.deleteDatabase(DATABASE)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(DATABASE), null).use { old ->
            old.execSQL("CREATE TABLE users (_id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT UNIQUE NOT NULL, password_hash BLOB NOT NULL, password_salt BLOB NOT NULL, password_iterations INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE auth_session (_id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(_id))")
            val digest = PasswordHasher().hash(PASSWORD)
            old.execSQL("INSERT INTO users VALUES (?, ?, ?, ?, ?, ?)", arrayOf(1, "Nguyễn Thị Mỹ", "existing@example.com", digest.hash, digest.salt, digest.iterations))
            old.execSQL("INSERT INTO auth_session VALUES (1, 1)")
            CatalogSchema.create(old)
            old.execSQL("INSERT INTO favorites VALUES (1, 'cleanser')")
            old.execSQL("UPDATE products SET price_vnd = 199000 WHERE id = 'cleanser'")
            old.version = 2
        }
        helper = AuthDatabaseHelper(context, DATABASE)
        repository = CartRepository(helper)
        assertEquals("Nguyễn Thị Mỹ", AuthRepository(helper).restoreSession()?.name)
        assertEquals(setOf("cleanser"), CatalogRepository(helper).load(1).favoriteIds)
        repository.add(1, "cleanser")
        assertEquals(199_000L, repository.load(1).totalVnd)
        assertEquals(AuthContract.DATABASE_VERSION, helper.readableDatabase.version)
        assertTrue(AuthRepository(helper).login("existing@example.com", PASSWORD) is AuthResult.Success)
    }

    private suspend fun expectCart(error: CartError, block: suspend () -> Unit) {
        try { block(); fail("Expected $error") } catch (exception: CartException) { assertEquals(error, exception.error) }
    }

    private companion object {
        const val DATABASE = "cart_repository_test.db"
        const val PASSWORD = "password123"
        val VALID_SHIPPING = ShippingDetails("Nguyễn Thị Mỹ", "0912345678", "12 Nguyễn Văn A, TP Hồ Chí Minh")
    }
}
