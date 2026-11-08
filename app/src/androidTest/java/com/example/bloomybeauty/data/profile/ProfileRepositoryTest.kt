package com.example.bloomybeauty.data.profile

import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.address.AddressRepository
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
        context.deleteDatabase(DATABASE); helper = AuthDatabaseHelper(context,DATABASE)
        profile = ProfileRepository(helper); auth = AuthRepository(helper)
        userId = (auth.register("Nguyễn Thị Mỹ","profile@example.com",PASSWORD,PASSWORD) as AuthResult.Success).user.id
    }
    @After fun tearDown() { helper.close(); context.deleteDatabase(DATABASE) }
    @Test fun namePersistsWithoutChangingCredentialsAddressBookOrCheckoutDraft() = runBlocking {
        val addresses = AddressRepository(helper)
        val original = addresses.save(userId,"Nhà",SHIPPING,false)
        CartRepository(helper).add(userId,"cleanser")
        CartRepository(helper).saveShipping(userId,SHIPPING.copy(address = "99 Nguyễn Văn B, TP Hồ Chí Minh"))
        val draft = CartRepository(helper).load(userId).shipping
        val saved = profile.save(userId,"  Trần Thị Mỹ  ")
        assertEquals("Trần Thị Mỹ",saved.user.name)
        assertEquals(original,addresses.list(userId)); assertEquals(draft,CartRepository(helper).load(userId).shipping)
        helper.close(); helper = AuthDatabaseHelper(context,DATABASE); profile = ProfileRepository(helper); auth = AuthRepository(helper)
        assertEquals(saved,profile.load(userId)); auth.logout()
        assertEquals("Trần Thị Mỹ",(auth.login("profile@example.com",PASSWORD) as AuthResult.Success).user.name)
    }
    @Test fun renameDoesNotChangeOrderOrPendingCheckout() = runBlocking {
        val cart = CartRepository(helper);cart.add(userId,"cleanser");cart.saveShipping(userId,SHIPPING)
        val orders = OrderRepository(helper);val request = orders.prepare(userId)
        profile.save(userId,"Tên mới")
        val order = orders.create(userId,request)
        assertEquals(SHIPPING,order.shipping)
        profile.save(userId,"Tên tiếp theo")
        assertEquals(order,orders.detail(userId,order.id))
    }
    @Test fun invalidNameAndFailedWritePreserveProfileAndAddresses() = runBlocking {
        val addresses = AddressRepository(helper).save(userId,"Nhà",SHIPPING,false)
        expect(ProfileError.NAME) {profile.save(userId," ")}
        helper.writableDatabase.execSQL("CREATE TRIGGER fail_profile BEFORE UPDATE ON users BEGIN SELECT RAISE(ABORT,'test failure'); END")
        try {profile.save(userId,"Tên mới");fail("Expected rollback")} catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals("Nguyễn Thị Mỹ",profile.load(userId).user.name)
        assertEquals(addresses,AddressRepository(helper).list(userId))
    }
    @Test fun inactiveSessionCannotReadOrRenameAnotherProfile() = runBlocking {
        val other = (auth.register("Trần Minh","other@profile.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        expect(ProfileError.SESSION) {profile.load(userId)}
        expect(ProfileError.SESSION) {profile.save(userId,"Tên sai")}
        assertEquals("Trần Minh",profile.load(other).user.name)
        auth.login("profile@example.com",PASSWORD)
        assertEquals("Nguyễn Thị Mỹ",profile.load(userId).user.name)
    }
    private suspend fun expect(error: ProfileError, block: suspend () -> Unit) {
        try {block();fail("Expected $error")} catch(e: ProfileException) {assertEquals(error,e.error)}
    }
    companion object {
        private const val DATABASE = "profile_repository_test.db"
        private const val PASSWORD = "password123"
        private val SHIPPING = ShippingDetails("Nguyễn Thị Mỹ","0912345678","12 Nguyễn Văn A, TP Hồ Chí Minh")
    }
}
