package com.example.bloomybeauty.data.notification

import android.database.sqlite.SQLiteException
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.admin.AdminRepository
import com.example.bloomybeauty.data.admin.AdminSchema
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.events.OrderChangePublisher
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class NotificationRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var auth: AuthRepository
    private lateinit var inbox: NotificationRepository
    private lateinit var cart: CartRepository
    private lateinit var orders: OrderRepository
    private var owner=0L
    private var admin=0L
    @Before fun setup(): Unit = runBlocking {
        context.deleteDatabase(DB);helper=AuthDatabaseHelper(context,DB);auth=AuthRepository(helper)
        owner=(auth.register("Khách hộp thư","owner@inbox.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        admin=(auth.register("Quản trị","admin@inbox.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        helper.writableDatabase.execSQL("UPDATE users SET role='ADMIN' WHERE _id=?",arrayOf(admin))
        auth.login("owner@inbox.test",PASSWORD)
        inbox=NotificationRepository(helper);cart=CartRepository(helper);orders=OrderRepository(helper)
        cart.add(owner,"cleanser");cart.saveShipping(owner,ShippingDetails("Nguyễn Ánh Hương","0912345678","123 Nguyễn Huệ, Thành phố Hồ Chí Minh"))
    }
    @After fun cleanup() {helper.close();context.deleteDatabase(DB)}
    private suspend fun create()=orders.create(owner,orders.prepare(owner))
    private fun count(table: String)=helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table",null).use {it.moveToFirst();it.getLong(0)}
    @Test fun createCancelAndRetriesProduceExactlyOneInboxRowPerAudit(): Unit=runBlocking {
        val request=orders.prepare(owner);val order=orders.create(owner,request);orders.create(owner,request)
        assertEquals(1L,inbox.page(owner).unread)
        orders.cancel(owner,order.id);orders.cancel(owner,order.id)
        val page=inbox.page(owner);assertEquals(2L,page.unread);assertEquals(2,count("user_notifications").toInt())
        assertEquals(count("order_events"),count("user_notifications"));assertEquals(2,page.items.map {it.eventId}.distinct().size)
        assertTrue(page.items.none {it.body.contains("0912345678") || it.body.contains("Nguyễn Huệ")})
    }
    @Test fun adminStatusEventsBelongToOrderOwnerAndOpenCurrentSnapshot(): Unit=runBlocking {
        val order=create();auth.login("admin@inbox.test",PASSWORD)
        val adminRepo=AdminRepository(helper)
        adminRepo.transition(admin,order.id,"PENDING","CONFIRMED")
        adminRepo.transition(admin,order.id,"CONFIRMED","SHIPPING")
        adminRepo.transition(admin,order.id,"SHIPPING","DELIVERED")
        assertTrue(inbox.page(admin).items.isEmpty())
        auth.login("owner@inbox.test",PASSWORD)
        val page=inbox.page(owner);assertEquals(4L,page.unread)
        assertEquals("DELIVERED",inbox.open(owner,page.items.last().id).status)
        assertEquals(3L,inbox.page(owner).unread)
        assertEquals("DELIVERED",orders.detail(owner,order.id)!!.status)
    }
    @Test fun inboxFailureRollsBackOrderStockAuditAndAllowsSameRequestRetry(): Unit=runBlocking {
        val request=orders.prepare(owner)
        val before=helper.readableDatabase.rawQuery("SELECT stock FROM products WHERE id='cleanser'",null).use {it.moveToFirst();it.getInt(0)}
        helper.writableDatabase.execSQL("CREATE TRIGGER fail_inbox BEFORE INSERT ON user_notifications BEGIN SELECT RAISE(ABORT,'inbox fixture failure'); END")
        try {orders.create(owner,request);fail("Expected rollback")} catch(_: SQLiteException) {}
        assertEquals(0L,count("orders"));assertEquals(0L,count("order_events"));assertEquals(0L,count("user_notifications"))
        val after=helper.readableDatabase.rawQuery("SELECT stock FROM products WHERE id='cleanser'",null).use {it.moveToFirst();it.getInt(0)}
        assertEquals(before,after);assertEquals(1,cart.load(owner).quantity)
        helper.writableDatabase.execSQL("DROP TRIGGER fail_inbox")
        orders.create(owner,request);assertEquals(1L,count("orders"));assertEquals(1L,count("user_notifications"))
    }
    @Test fun postCommitNotificationFailureDoesNotTurnSuccessfulOrderIntoFailure(): Unit=runBlocking {
        orders=OrderRepository(helper,OrderChangePublisher {throw IllegalStateException("notification fixture failure")})
        val request=orders.prepare(owner);val order=orders.create(owner,request)
        assertEquals(order.id,orders.create(owner,request).id)
        assertEquals(1L,inbox.page(owner).unread);assertEquals(1L,count("orders"))
        assertEquals("CANCELLED",orders.cancel(owner,order.id).status)
        assertEquals(2L,inbox.page(owner).unread)
    }
    @Test fun readAllAndOpenAreStrictlySessionAndOwnerScoped(): Unit=runBlocking {
        create();val row=inbox.page(owner).items.single()
        auth.login("admin@inbox.test",PASSWORD)
        try {inbox.readAll(owner);fail("Expected stale session")} catch(_: SessionException) {}
        try {inbox.open(admin,row.id);fail("Expected wrong owner denial")} catch(_: NotificationUnavailable) {}
        assertTrue(inbox.page(admin).items.isEmpty())
        auth.login("owner@inbox.test",PASSWORD)
        assertEquals(1L,inbox.page(owner).unread);inbox.readAll(owner);inbox.readAll(owner)
        assertEquals(0L,inbox.page(owner).unread);assertEquals("PENDING",orders.list(owner).single().status)
    }
    @Test fun stablePaginationAndReopenPreserveAllEvents(): Unit=runBlocking {
        val order=create();val db=helper.writableDatabase
        db.beginTransaction()
        try {repeat(64) {AdminSchema.record(db,order.id,owner,"PENDING","CONFIRMED")};db.setTransactionSuccessful()} finally {db.endTransaction()}
        db.execSQL("UPDATE user_notifications SET created_at=1000")
        helper.close();helper=AuthDatabaseHelper(context,DB);inbox=NotificationRepository(helper)
        val one=inbox.page(owner);val two=inbox.page(owner,one.next);val three=inbox.page(owner,two.next)
        assertEquals(30,one.items.size);assertEquals(30,two.items.size);assertEquals(5,three.items.size);assertNull(three.next)
        val ids=(one.items+two.items+three.items).map {it.id}
        assertEquals(65,ids.distinct().size);assertEquals(ids.sortedDescending(),ids);assertEquals(65L,three.unread)
    }
    @Test fun missingEntityLeavesHistoryAndReportsUnavailable(): Unit=runBlocking {
        create();val row=inbox.page(owner).items.single()
        helper.writableDatabase.execSQL("DELETE FROM order_events");helper.writableDatabase.execSQL("DELETE FROM orders")
        try {inbox.open(owner,row.id);fail("Expected missing target")} catch(_: NotificationUnavailable) {}
        assertEquals(1L,inbox.page(owner).unread)
    }
    @Test fun migrationFromTenBackfillsAuditTimeOnceAndPreservesCommerce(): Unit=runBlocking {
        val order=create();val original=inbox.page(owner).items.single()
        helper.writableDatabase.execSQL("DROP TABLE user_notifications");helper.writableDatabase.version=10
        helper.close();helper=AuthDatabaseHelper(context,DB);inbox=NotificationRepository(helper);orders=OrderRepository(helper)
        val row=inbox.page(owner).items.single()
        assertEquals(original.eventId,row.eventId);assertEquals(original.createdAt,row.createdAt)
        assertEquals(order,orders.detail(owner,order.id));assertEquals(11,helper.readableDatabase.version)
        helper.close();helper=AuthDatabaseHelper(context,DB);inbox=NotificationRepository(helper)
        assertEquals(1,inbox.page(owner).items.size)
    }
    companion object {private const val DB="notification-repository-test.db";private const val PASSWORD="InboxTest123!"}
}
