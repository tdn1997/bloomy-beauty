package com.example.bloomybeauty.data.review

import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.CartRepository
import com.example.bloomybeauty.data.cart.ShippingDetails
import com.example.bloomybeauty.data.order.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReviewRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var auth: AuthRepository
    private lateinit var reviews: ReviewRepository
    private var owner = 0L
    private var other = 0L
    private var orderId = 0L
    private val password = "StrongPassword123"
    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB)
        helper = AuthDatabaseHelper(context, DB); auth = AuthRepository(helper); reviews = ReviewRepository(helper)
        other = (auth.register("Người khác", "other@review.test", password, password) as AuthResult.Success).user.id
        owner = (auth.register("Người mua", "owner@review.test", password, password) as AuthResult.Success).user.id
        val cart = CartRepository(helper)
        cart.add(owner, "cleanser")
        cart.saveShipping(owner, ShippingDetails("Người mua", "0912345678", "123 Nguyễn Huệ, Thành phố Hồ Chí Minh"))
        val orders = OrderRepository(helper)
        orderId = orders.create(owner, orders.prepare(owner)).id
    }
    @After fun cleanup() { helper.close(); context.deleteDatabase(DB) }
    private fun delivered() {helper.writableDatabase.execSQL("UPDATE orders SET status='DELIVERED' WHERE _id=?", arrayOf(orderId))}
    private suspend fun rejected(block: suspend () -> Unit) {
        try {block(); fail("Expected review validation error")} catch (_: ReviewException) { }
    }
    @Test fun onlyDeliveredPurchasedProductCanBeReviewed() = runBlocking {
        rejected { reviews.save(owner,orderId,"cleanser",5,"Tốt",null) }
        delivered()
        rejected { reviews.save(owner,orderId,"serum",5,"Tốt",null) }
        auth.login("other@review.test",password)
        rejected { reviews.save(other,orderId,"cleanser",5,"Tốt",null) }
        assertEquals(0,reviews.list(other,"cleanser").count)
        try {reviews.list(owner,"cleanser"); fail("Stale session")} catch (_: SessionException) { }
    }
    @Test fun createEditConflictDeleteAndAggregate() = runBlocking {
        delivered()
        reviews.save(owner,orderId,"cleanser",5,"  Dùng tốt  ",null)
        val first = reviews.own(owner,orderId,"cleanser")!!
        assertEquals("Dùng tốt",first.comment)
        assertEquals(5.0,reviews.list(owner,"cleanser").average,0.001)
        rejected { reviews.save(owner,orderId,"cleanser",1,"Duplicate",null) }
        reviews.save(owner,orderId,"cleanser",3,"Cập nhật",first.token)
        rejected {reviews.save(owner,orderId,"cleanser",2,"Sửa cũ",first.token)}
        rejected {reviews.delete(owner,first)}
        val current = reviews.own(owner,orderId,"cleanser")!!
        assertEquals(first.id,current.id); assertEquals(1L,current.revision)
        assertEquals(1,reviews.list(owner,"cleanser").count)
        assertEquals(3.0,reviews.list(owner,"cleanser").average,0.001)
        auth.login("other@review.test",password)
        rejected {reviews.delete(other,current)}
        auth.login("owner@review.test",password)
        reviews.delete(owner,current)
        assertEquals(0,reviews.list(owner,"cleanser").count)
        reviews.save(owner,orderId,"cleanser",4,"Viết lại",null)
    }
    @Test fun repeatedPurchasesHaveIndependentReviewsAndStaleDeletedDraftCannotOverwrite() = runBlocking {
        delivered()
        reviews.save(owner,orderId,"cleanser",5,"Lần đầu",null)
        val first = reviews.own(owner,orderId,"cleanser")!!
        val orders = OrderRepository(helper)
        orders.acknowledge(owner, orders.detail(owner,orderId)!!.requestId)
        CartRepository(helper).add(owner,"cleanser")
        val second = orders.create(owner,orders.prepare(owner))
        helper.writableDatabase.execSQL("UPDATE orders SET status='DELIVERED' WHERE _id=?",arrayOf(second.id))
        reviews.save(owner,second.id,"cleanser",3,"Mua lại",null)
        val page = reviews.list(owner,"cleanser")
        assertEquals(2,page.count); assertEquals(4.0,page.average,0.001)
        assertEquals(second.id,page.reviews.first().orderId)
        reviews.delete(owner,first)
        reviews.save(owner,orderId,"cleanser",4,"Viết mới",null)
        rejected {reviews.save(owner,orderId,"cleanser",1,"Bản cũ",first.token)}
        assertEquals("Viết mới",reviews.own(owner,orderId,"cleanser")!!.comment)
    }
    @Test fun invalidInputDoesNotWrite() = runBlocking {
        delivered()
        rejected {reviews.save(owner,orderId,"cleanser",0,"Tốt",null)}
        rejected {reviews.save(owner,orderId,"cleanser",6,"Tốt",null)}
        rejected {reviews.save(owner,orderId,"cleanser",5,"  ",null)}
        rejected {reviews.save(owner,orderId,"cleanser",5,"x".repeat(1001),null)}
        assertEquals(0,reviews.list(owner,"cleanser").count)
    }
    @Test fun migrationFromSevenPreservesOrdersAndCreatesReviews() = runBlocking {
        helper.close()
        android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(DB).path,null,0).use { db ->
            db.execSQL("DROP TABLE user_notifications");db.execSQL("DROP TABLE coupon_rules");db.execSQL("DROP TABLE addresses");db.execSQL("DROP TABLE product_reviews")
            com.example.bloomybeauty.fixtures.LegacyOrders.rebuild(db,true)
            db.version = 7
        }
        helper = AuthDatabaseHelper(context, DB); reviews = ReviewRepository(helper)
        assertEquals(AuthContract.DATABASE_VERSION, helper.writableDatabase.version)
        delivered()
        reviews.save(owner,orderId,"cleanser",5,"Sau nâng cấp",null)
        assertEquals(1,reviews.list(owner,"cleanser").count)
        assertEquals(orderId, reviews.own(owner,orderId,"cleanser")!!.orderId)
        helper.readableDatabase.rawQuery("PRAGMA foreign_key_check",null).use {assertFalse(it.moveToFirst())}
        helper.readableDatabase.rawQuery("PRAGMA integrity_check",null).use {it.moveToFirst();assertEquals("ok",it.getString(0))}
    }
    companion object {private const val DB = "reviews-test.db"}
}
