package com.example.bloomybeauty.data.admin

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.data.profile.ProfileRepository
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AdminRepositoryTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var auth: AuthRepository
    private lateinit var admin: AdminRepository
    private lateinit var orders: OrderRepository
    private var adminId=0L
    private var customerId=0L
    private val password="StrongPassword123"
    private val shipping=ShippingDetails("Nguyễn Thị Mỹ","0912345678","123 Đường Nguyễn Huệ, Thành phố Hồ Chí Minh")
    @Before fun setup(): Unit = runBlocking {
        context.deleteDatabase(DB); helper=AuthDatabaseHelper(context,DB);auth=AuthRepository(helper);admin=AdminRepository(helper);orders=OrderRepository(helper)
        customerId=(auth.register("Khách hàng","customer@example.com",password,password) as AuthResult.Success).user.id
        adminId=(auth.register("Quản trị","admin@example.com",password,password) as AuthResult.Success).user.id
        helper.writableDatabase.execSQL("UPDATE users SET role='ADMIN' WHERE _id=?",arrayOf(adminId))
        auth.login("admin@example.com",password)
    }
    @After fun cleanup() {helper.close();context.deleteDatabase(DB)}
    private suspend fun expect(error: AdminError, block:suspend()->Unit) {
        try {block();fail("Expected $error")} catch(e:AdminException) {assertEquals(error,e.error)}
    }
    private suspend fun createOrder(quantity:Int=1): CustomerOrder {
        auth.login("customer@example.com",password)
        val cart=CartRepository(helper);cart.add(customerId,"cleanser");cart.setQuantity(customerId,"cleanser",quantity);cart.saveShipping(customerId,shipping)
        val order=orders.create(customerId,orders.prepare(customerId));auth.login("admin@example.com",password);return order
    }
    // Trusted fixture inspection; customer endpoints now require the customer session.
    private fun readOrder(id:Long):CustomerOrder = helper.readableDatabase.rawQuery("SELECT * FROM orders WHERE _id=?",arrayOf(id.toString())).use {it.moveToFirst();orders.readOrder(helper.readableDatabase,it)}
    private fun stock()=helper.readableDatabase.rawQuery("SELECT stock FROM products WHERE id='cleanser'",null).use{it.moveToFirst();it.getInt(0)}
    @Test fun customerSignupRestoreLoginAndProfilePreserveTrustedRole() = runBlocking {
        assertEquals(UserRole.ADMIN,auth.restoreSession()!!.role)
        assertEquals(UserRole.ADMIN,ProfileRepository(helper).save(adminId,"Quản trị mới",null).user.role)
        auth.login("customer@example.com",password)
        assertEquals(UserRole.CUSTOMER,auth.restoreSession()!!.role)
        expect(AdminError.PERMISSION){admin.load(customerId)}
        expect(AdminError.PERMISSION){admin.load(adminId)}
    }
    @Test fun revokedRoleBlocksEveryReadAndWrite()=runBlocking {
        val p=admin.load(adminId).products.first()
        val order=createOrder()
        helper.writableDatabase.execSQL("UPDATE users SET role='CUSTOMER' WHERE _id=?",arrayOf(adminId))
        expect(AdminError.PERMISSION){admin.load(adminId)}
        expect(AdminError.PERMISSION){admin.save(adminId,p.copy(stock=500),false)}
        expect(AdminError.PERMISSION){admin.transition(adminId,order.id,"PENDING","CONFIRMED")}
        assertEquals(29,stock());assertEquals("PENDING",readOrder(order.id).status)
    }
    @Test fun addEditHideAndUnhideKeepOrderSnapshots()=runBlocking {
        val order=createOrder()
        val original=admin.load(adminId).products.first{it.id=="cleanser"}
        val added=admin.save(adminId,original.copy(name="Sản phẩm mới"),true)
        assertTrue(admin.load(adminId).products.any{it.id==added})
        admin.save(adminId,original.copy(name="Tên mới",priceVnd=200000,stock=40,active=false),false)
        assertFalse(CatalogRepository(helper).load(adminId).products.any{it.id==original.id})
        assertEquals(original.name,readOrder(order.id).lines.single().name)
        val hidden=admin.load(adminId).products.first{it.id==original.id}
        admin.save(adminId,hidden.copy(active=true),false)
        assertTrue(CatalogRepository(helper).load(adminId).products.any{it.id==original.id})
    }
    @Test fun staleProductDraftCannotOverwriteCheckoutOrAnotherEdit()=runBlocking {
        val original=admin.load(adminId).products.first{it.id=="cleanser"}
        createOrder()
        expect(AdminError.CONFLICT){admin.save(adminId,original.copy(stock=500),false)}
        val current=admin.load(adminId).products.first{it.id=="cleanser"}
        admin.save(adminId,current.copy(priceVnd=200000),false)
        expect(AdminError.CONFLICT){admin.save(adminId,current.copy(stock=500),false)}
        assertEquals(29,stock())
    }
    @Test fun staleProductDraftCannotOverwriteCancelledStock()=runBlocking {
        val o=createOrder();val p=admin.load(adminId).products.first{it.id=="cleanser"}
        admin.transition(adminId,o.id,"PENDING","CANCELLED")
        expect(AdminError.CONFLICT){admin.save(adminId,p.copy(stock=500),false)};assertEquals(30,stock())
    }
    @Test fun invalidProductDoesNotWrite()=runBlocking {
        val p=admin.load(adminId).products.first()
        expect(AdminError.INVALID_PRODUCT){admin.save(adminId,p.copy(stock=-1),false)}
        expect(AdminError.INVALID_PRODUCT){admin.save(adminId,p.copy(categoryId="unknown"),false)}
        assertEquals(p,admin.load(adminId).products.first())
    }
    @Test fun processAllOrdersWithAuditAndTerminalProtection()=runBlocking {
        val o=createOrder()
        admin.transition(adminId,o.id,"PENDING","CONFIRMED")
        admin.transition(adminId,o.id,"CONFIRMED","SHIPPING")
        expect(AdminError.INVALID_TRANSITION){admin.transition(adminId,o.id,"SHIPPING","CANCELLED")}
        admin.transition(adminId,o.id,"SHIPPING","DELIVERED")
        assertEquals(29,stock())
        val snapshot=admin.load(adminId)
        assertEquals("DELIVERED",snapshot.orders.single().status)
        assertEquals(listOf("PENDING","CONFIRMED","SHIPPING","DELIVERED"),snapshot.events[o.id]!!.map{it.to})
        assertEquals("Khách hàng",snapshot.events[o.id]!!.first().actor)
        assertEquals("Quản trị",snapshot.events[o.id]!!.last().actor)
        assertTrue(snapshot.events[o.id]!!.all{it.time>0})
        expect(AdminError.INVALID_TRANSITION){admin.transition(adminId,o.id,"DELIVERED","CANCELLED")}
    }
    @Test fun concurrentConfirmedCancellationRestoresOnce()=runBlocking {
        val o=createOrder(2);admin.transition(adminId,o.id,"PENDING","CONFIRMED")
        coroutineScope {(1..8).map{async(Dispatchers.IO){admin.transition(adminId,o.id,"CONFIRMED","CANCELLED")}}.awaitAll()}
        assertEquals(30,stock());assertEquals(1,admin.load(adminId).events[o.id]!!.count{it.to=="CANCELLED"})
    }
    @Test fun staleOrderStatusCannotSkipOrReverseTransitions()=runBlocking {
        val o=createOrder();admin.transition(adminId,o.id,"PENDING","CONFIRMED")
        expect(AdminError.CONFLICT){admin.transition(adminId,o.id,"PENDING","CANCELLED")}
        expect(AdminError.INVALID_TRANSITION){admin.transition(adminId,o.id,"CONFIRMED","DELIVERED")}
        assertEquals("CONFIRMED",admin.load(adminId).orders.single().status);assertEquals(29,stock())
    }
    @Test fun auditFailureRollsBackStateAndStock()=runBlocking {
        val o=createOrder()
        helper.writableDatabase.execSQL("CREATE TRIGGER fail_event BEFORE INSERT ON order_events BEGIN SELECT RAISE(ABORT,'test failure'); END")
        try {admin.transition(adminId,o.id,"PENDING","CANCELLED");fail("Expected rollback")}catch(_:android.database.sqlite.SQLiteException){}
        assertEquals(29,stock());assertEquals("PENDING",readOrder(o.id).status)
    }
    @Test fun overflowRollsBackCancellation()=runBlocking {
        val o=createOrder();helper.writableDatabase.execSQL("UPDATE products SET stock=2147483647 WHERE id='cleanser'")
        expect(AdminError.STOCK_OVERFLOW){admin.transition(adminId,o.id,"PENDING","CANCELLED")}
        assertEquals("PENDING",readOrder(o.id).status)
    }
    @Test fun migrationFromFourPreservesCustomersOrdersAndSession()=runBlocking {
        val o=createOrder()
        helper.close()
        SQLiteDatabase.openDatabase(context.getDatabasePath(DB).path,null,SQLiteDatabase.OPEN_READWRITE).use {db ->
            db.execSQL("DROP TABLE order_events");db.execSQL("ALTER TABLE users DROP COLUMN role");db.execSQL("ALTER TABLE products DROP COLUMN revision");db.version=4
        }
        helper=AuthDatabaseHelper(context,DB);auth=AuthRepository(helper);admin=AdminRepository(helper);orders=OrderRepository(helper)
        assertEquals(5,helper.readableDatabase.version);assertEquals(UserRole.CUSTOMER,auth.restoreSession()!!.role)
        assertEquals(o,readOrder(o.id));assertEquals(29,stock())
        expect(AdminError.PERMISSION){admin.load(adminId)}
    }
    companion object {private const val DB="admin-repository-test.db"}
}
