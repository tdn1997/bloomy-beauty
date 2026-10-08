package com.example.bloomybeauty.data.profile

import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.OrderRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProfileRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var profile: ProfileRepository
    private lateinit var auth: AuthRepository
    private var userId = 0L
    @Before fun setUp() = runBlocking {
        context.deleteDatabase(DATABASE)
        helper = AuthDatabaseHelper(context, DATABASE)
        profile = ProfileRepository(helper); auth = AuthRepository(helper)
        userId = (auth.register("Nguyễn Thị Mỹ", "profile@example.com", PASSWORD, PASSWORD) as AuthResult.Success).user.id
    }
    @After fun tearDown() { helper.close(); context.deleteDatabase(DATABASE) }

    @Test fun profileAndDefaultAddressPersistWithoutChangingCredentials() = runBlocking {
        val saved = profile.save(userId, "  Trần Thị Mỹ  ", SHIPPING.copy(phone = "+84 912 345 678"))
        assertEquals("Trần Thị Mỹ", saved.user.name)
        assertEquals("0912345678", saved.shipping?.phone)
        helper.close()
        helper = AuthDatabaseHelper(context, DATABASE)
        profile = ProfileRepository(helper); auth = AuthRepository(helper)
        assertEquals(saved, profile.load(userId))
        auth.logout()
        val loggedIn = auth.login("profile@example.com", PASSWORD) as AuthResult.Success
        assertEquals("Trần Thị Mỹ", loggedIn.user.name)
    }

    @Test fun nameOnlyProfileCanClearDefaultAddressAndDoesNotChangeExistingOrders() = runBlocking {
        profile.save(userId, "Nguyễn Thị Mỹ", SHIPPING)
        val cart = CartRepository(helper)
        cart.add(userId, "cleanser")
        val orders = OrderRepository(helper)
        val order = orders.create(userId, orders.prepare(userId))
        profile.save(userId, "Trần Minh", SHIPPING.copy(recipient = "Trần Minh", address = "99 Nguyễn Văn B, TP Hồ Chí Minh"))
        assertEquals(SHIPPING, orders.detail(userId, order.id)?.shipping)
        profile.save(userId, "Tên mới", null)
        assertNull(profile.load(userId).shipping)
        assertEquals("Tên mới", auth.restoreSession()?.name)
        assertEquals(SHIPPING, orders.detail(userId, order.id)?.shipping)
    }

    @Test fun invalidFieldsCannotPartiallyChangeNameOrAddress() = runBlocking {
        profile.save(userId, "Nguyễn Thị Mỹ", SHIPPING)
        expect(ProfileError.NAME) { profile.save(userId, " ", SHIPPING) }
        expect(ProfileError.RECIPIENT) { profile.save(userId, "Tên mới", SHIPPING.copy(recipient = "")) }
        expect(ProfileError.PHONE) { profile.save(userId, "Tên mới", SHIPPING.copy(phone = "123")) }
        expect(ProfileError.ADDRESS) { profile.save(userId, "Tên mới", SHIPPING.copy(address = "ngắn")) }
        assertEquals("Nguyễn Thị Mỹ", profile.load(userId).user.name)
        assertEquals(SHIPPING, profile.load(userId).shipping)
    }

    @Test fun failedAddressWriteRollsBackNameChange() = runBlocking {
        profile.save(userId, "Nguyễn Thị Mỹ", SHIPPING)
        helper.writableDatabase.execSQL("CREATE TRIGGER fail_profile BEFORE UPDATE ON shipping_details BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        try { profile.save(userId, "Tên mới", SHIPPING.copy(address = "99 Nguyễn Văn B, TP Hồ Chí Minh")); fail("Expected rollback") }
        catch (exception: android.database.sqlite.SQLiteException) { /* simulated write failure */ }
        assertEquals("Nguyễn Thị Mỹ", profile.load(userId).user.name)
        assertEquals(SHIPPING, profile.load(userId).shipping)
    }

    @Test fun inactiveSessionCannotReadOrOverwriteAnotherProfile() = runBlocking {
        profile.save(userId, "Nguyễn Thị Mỹ", SHIPPING)
        val other = (auth.register("Trần Minh", "profileother@example.com", PASSWORD, PASSWORD) as AuthResult.Success).user.id
        expect(ProfileError.SESSION) { profile.load(userId) }
        expect(ProfileError.SESSION) { profile.save(userId, "Tên sai", null) }
        assertNull(profile.load(other).shipping)
        auth.login("profile@example.com", PASSWORD)
        assertEquals(SHIPPING, profile.load(userId).shipping)
    }

    private suspend fun expect(error: ProfileError, block: suspend () -> Unit) {
        try { block(); fail("Expected $error") } catch (exception: ProfileException) { assertEquals(error, exception.error) }
    }
    private companion object {
        const val DATABASE = "profile_repository_test.db"
        const val PASSWORD = "password123"
        val SHIPPING = ShippingDetails("Nguyễn Thị Mỹ", "0912345678", "12 Nguyễn Văn A, TP Hồ Chí Minh")
    }
}
