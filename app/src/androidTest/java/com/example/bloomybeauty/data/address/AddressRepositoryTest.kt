package com.example.bloomybeauty.data.address

import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.data.review.ReviewRepository
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AddressRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var auth: AuthRepository
    private lateinit var repository: AddressRepository
    private var owner = 0L
    private val shipping = ShippingDetails("Nguyễn Thị Mỹ","0912345678","123 Nguyễn Huệ, Thành phố Hồ Chí Minh")
    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB); helper = AuthDatabaseHelper(context,DB); auth = AuthRepository(helper);repository = AddressRepository(helper)
        owner = (auth.register("Người mua","address@bloomy.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
    }
    @After fun cleanup() {helper.close();context.deleteDatabase(DB)}
    private suspend fun rejected(block: suspend () -> Unit) {
        try {block();fail("Expected address validation/conflict")} catch (_: AddressException) { }
    }
    @Test fun firstAddressIsDefaultAndFieldsAreNormalized() = runBlocking {
        val first = repository.save(owner,"  Nhà  ",shipping.copy(phone = "+84 912 345 678"),false).single()
        assertEquals("Nhà",first.label);assertEquals(shipping,first.shipping);assertTrue(first.isDefault)
        repository.save(owner,"Văn phòng",shipping.copy(address = "456 Lê Lợi, Thành phố Hồ Chí Minh"),false)
        val rows = repository.list(owner)
        assertEquals(2,rows.size);assertEquals(1,rows.count {it.isDefault})
        assertEquals(first.id,rows.first().id)
        helper.close(); helper = AuthDatabaseHelper(context,DB);repository = AddressRepository(helper)
        assertEquals(rows,repository.list(owner))
    }
    @Test fun defaultChangesAndDeletionAreAtomicAndInvalidateStaleTokens() = runBlocking {
        val a = repository.save(owner,"Nhà",shipping,false).single()
        val b = repository.save(owner,"Văn phòng",shipping,false).first {it.id != a.id}
        val changed = repository.setDefault(owner,b.token)
        assertEquals(b.id,changed.single {it.isDefault}.id)
        assertEquals(changed,repository.setDefault(owner,changed.first().token))
        rejected {repository.save(owner,"Tên cũ",shipping,true,a.token)}
        rejected {repository.select(owner,a.token)}
        rejected {repository.delete(owner,b.token)}
        val restored = repository.delete(owner,changed.single {it.id == b.id}.token)
        assertEquals(a.id,restored.single().id);assertTrue(restored.single().isDefault)
        assertTrue(repository.delete(owner,restored.single().token).isEmpty())
    }
    @Test fun failedNewDefaultRollsBackBothRows() = runBlocking {
        repository.save(owner,"Nhà",shipping,false)
        val b = repository.save(owner,"Văn phòng",shipping,false).first { !it.isDefault }
        val before = repository.list(owner)
        helper.writableDatabase.execSQL("CREATE TRIGGER fail_default BEFORE UPDATE ON addresses WHEN NEW.is_default=1 BEGIN SELECT RAISE(ABORT,'test failure'); END")
        try {repository.setDefault(owner,b.token);fail("Expected rollback")} catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(before,repository.list(owner))
    }
    @Test fun addressesNeverMutateSelectedDraftOrOrderSnapshots() = runBlocking {
        val a = repository.save(owner,"Nhà",shipping,false).single()
        val selected = repository.select(owner,a.token)
        val cart = CartRepository(helper);cart.add(owner,"cleanser");cart.saveShipping(owner,selected)
        val orders = OrderRepository(helper);val order = orders.create(owner,orders.prepare(owner))
        val changed = repository.save(owner,"Nhà mới",shipping.copy(address = "999 Lê Lợi, Thành phố Hồ Chí Minh"),true,a.token).single()
        assertEquals(selected,cart.load(owner).shipping)
        assertEquals(selected,orders.detail(owner,order.id)!!.shipping)
        repository.delete(owner,changed.token)
        assertEquals(selected,cart.load(owner).shipping)
        assertEquals(selected,orders.detail(owner,order.id)!!.shipping)
    }
    @Test fun defaultIsUsedOnlyWhenCheckoutHasNoDraft() = runBlocking {
        repository.save(owner,"Nhà",shipping,false)
        val cart = CartRepository(helper)
        assertEquals(shipping,cart.load(owner).shipping)
        val b = repository.save(owner,"Văn phòng",shipping.copy(recipient = "Người nhận khác"),true).first()
        assertEquals(b.shipping,cart.load(owner).shipping)
        cart.add(owner,"cleanser")
        cart.saveShipping(owner,shipping)
        repository.setDefault(owner,repository.list(owner).single {it.label == "Nhà"}.token)
        assertEquals(shipping,cart.load(owner).shipping)
    }
    @Test fun crossAccountAndStaleSessionCannotAccessOrOverwrite() = runBlocking {
        val a = repository.save(owner,"Nhà",shipping,false).single()
        val other = (auth.register("Khách khác","other@address.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        rejected {repository.save(other,"Giả mạo",shipping,true,a.token)}
        rejected {repository.delete(other,a.token)}
        rejected {repository.setDefault(other,a.token)}
        rejected {repository.select(other,a.token)}
        try {repository.list(owner);fail("Expected stale session")} catch (_: SessionException) { }
        assertTrue(repository.list(other).isEmpty())
        auth.login("address@bloomy.test",PASSWORD)
        assertEquals(a,repository.list(owner).single())
    }
    @Test fun invalidInputDoesNotCreateOrChangeDefault() = runBlocking {
        rejected {repository.save(owner," ",shipping,true)}
        rejected {repository.save(owner,"x".repeat(41),shipping,true)}
        rejected {repository.save(owner,"Nhà",shipping.copy(phone = "123"),true)}
        rejected {repository.save(owner,"Nhà",shipping.copy(recipient = ""),true)}
        rejected {repository.save(owner,"Nhà",shipping.copy(address = "Ngắn"),true)}
        assertTrue(repository.list(owner).isEmpty())
    }
    @Test fun concurrentCreatesRespectLimitAndEditingAtLimitIsAllowed() = runBlocking {
        val success = AtomicInteger();val failures = AtomicInteger()
        coroutineScope { (1..14).map { index -> async(Dispatchers.IO) {
            try {repository.save(owner,"Địa chỉ $index",shipping,false);success.incrementAndGet()}
            catch (_: AddressException) {failures.incrementAndGet()}
        } }.awaitAll() }
        assertEquals(10,success.get());assertEquals(4,failures.get())
        val rows = repository.list(owner);assertEquals(10,rows.size);assertEquals(1,rows.count {it.isDefault})
        assertEquals(10,repository.save(owner,"Tên sửa",shipping,false,rows.last().token).size)
    }
    @Test fun deletedTokenCannotAffectRecreatedAddress() = runBlocking {
        val old = repository.save(owner,"Nhà",shipping,false).single()
        repository.delete(owner,old.token)
        val new = repository.save(owner,"Nhà",shipping,false).single()
        assertNotEquals(old.id,new.id)
        rejected {repository.delete(owner,old.token)}
        rejected {repository.save(owner,"Cũ",shipping,true,old.token)}
        assertEquals(new,repository.list(owner).single())
    }
    @Test fun versionEightMigrationImportsOnceAndPreservesExistingCommerceData() = runBlocking {
        val cart = CartRepository(helper);cart.add(owner,"cleanser");cart.saveShipping(owner,shipping)
        val orders = OrderRepository(helper);val order = orders.create(owner,orders.prepare(owner))
        helper.writableDatabase.execSQL("UPDATE orders SET status='DELIVERED',delivered_at=123 WHERE _id=?",arrayOf(order.id))
        ReviewRepository(helper).save(owner,order.id,"cleanser",5,"Đánh giá trước nâng cấp",null)
        cart.add(owner,"serum");CatalogRepository(helper).toggleFavorite(owner,"cleanser")
        helper.writableDatabase.execSQL("INSERT INTO care_routines(user_id,title,minute_of_day,days_mask,enabled,created_at,updated_at) VALUES(?,'Lịch cũ',540,127,0,1,1)",arrayOf(owner))
        helper.close()
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(DB).path,null,0).use { db ->
            db.execSQL("DROP TABLE user_notifications");db.execSQL("DROP TABLE coupon_rules");db.execSQL("DROP TABLE addresses")
            com.example.bloomybeauty.fixtures.LegacyOrders.rebuild(db,true)
            db.version = 8
        }
        helper = AuthDatabaseHelper(context,DB);repository = AddressRepository(helper)
        assertEquals(AuthContract.DATABASE_VERSION,helper.writableDatabase.version)
        assertEquals(shipping,repository.list(owner).single().shipping)
        assertTrue(repository.list(owner).single().isDefault)
        assertEquals(owner,AuthRepository(helper).restoreSession()!!.id)
        assertEquals("serum",CartRepository(helper).load(owner).items.single().product.id)
        assertEquals(setOf("cleanser"),CatalogRepository(helper).load(owner).favoriteIds)
        assertEquals(shipping,OrderRepository(helper).detail(owner,order.id)!!.shipping)
        assertEquals(1,ReviewRepository(helper).list(owner,"cleanser").count)
        helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM care_routines WHERE user_id=?",arrayOf("$owner")).use {it.moveToFirst();assertEquals(1,it.getInt(0))}
        helper.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use {assertFalse(it.moveToFirst())}
        helper.readableDatabase.rawQuery("PRAGMA integrity_check",null).use {it.moveToFirst();assertEquals("ok",it.getString(0))}
        helper.close();helper = AuthDatabaseHelper(context,DB)
        assertEquals(1,AddressRepository(helper).list(owner).size)
    }
    companion object {private const val DB = "address-repository-test.db";private const val PASSWORD = "AddressTest123!"}
}
