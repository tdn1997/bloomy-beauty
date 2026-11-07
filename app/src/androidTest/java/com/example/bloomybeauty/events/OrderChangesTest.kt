package com.example.bloomybeauty.events

import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.data.admin.AdminRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OrderChangesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun eventsFollowCommitAndIgnoreRetriesAndRollbacks() = runBlocking {
        val name = "order-events-test.db"
        context.deleteDatabase(name)
        val helper = AuthDatabaseHelper(context, name)
        try {
            val auth = AuthRepository(helper)
            val password = "CareTest123!"
            val customer = (auth.register("Khách", "events@example.com", password, password) as AuthResult.Success).user.id
            val received = mutableListOf<OrderChange>()
            val publisher = OrderChangePublisher { change ->
                assertFalse(helper.writableDatabase.inTransaction())
                helper.readableDatabase.rawQuery("SELECT status FROM orders WHERE _id=?", arrayOf(change.orderId.toString())).use { assertTrue(it.moveToFirst()) }
                received.add(change)
            }
            val orders = OrderRepository(helper, publisher)
            val cart = CartRepository(helper)
            cart.add(customer, "cleanser")
            cart.saveShipping(customer, ShippingDetails("Khách thử nghiệm", "0912345678", "123 Võ Văn Ngân, Thủ Đức, TP Hồ Chí Minh"))
            val request = orders.prepare(customer)
            helper.writableDatabase.execSQL("CREATE TRIGGER fail_order BEFORE INSERT ON order_items BEGIN SELECT RAISE(ABORT,'test'); END")
            try { orders.create(customer, request); fail("Expected rollback") } catch (_: android.database.sqlite.SQLiteException) { }
            assertTrue(received.isEmpty())
            helper.writableDatabase.execSQL("DROP TRIGGER fail_order")
            val order = orders.create(customer, request)
            orders.create(customer, request)
            assertEquals(listOf(OrderChange(order.id, customer, OrderChangeKind.CREATED)), received)
            orders.cancel(customer, order.id); orders.cancel(customer, order.id)
            assertEquals(2, received.size)
            assertEquals(OrderChangeKind.CANCELLED, received.last().kind)
            orders.acknowledge(customer, request.id)
            cart.add(customer, "cleanser")
            val second = orders.create(customer, orders.prepare(customer))
            val admin = (auth.register("Admin", "admin-events@example.com", password, password) as AuthResult.Success).user.id
            helper.writableDatabase.execSQL("UPDATE users SET role='ADMIN' WHERE _id=?", arrayOf(admin))
            val management = AdminRepository(helper, publisher)
            management.transition(admin, second.id, "PENDING", "CONFIRMED")
            management.transition(admin, second.id, "PENDING", "CONFIRMED")
            assertEquals(OrderChange(second.id, customer, OrderChangeKind.STATUS_CHANGED), received.last())
            assertEquals(4, received.size)
            helper.writableDatabase.execSQL("CREATE TRIGGER fail_audit BEFORE INSERT ON order_events BEGIN SELECT RAISE(ABORT,'test'); END")
            try { management.transition(admin, second.id, "CONFIRMED", "CANCELLED"); fail("Expected rollback") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(4, received.size)
        } finally { helper.close(); context.deleteDatabase(name) }
    }

    @Test fun packageScopedBroadcastReachesDynamicReceiver() {
        val received = CountDownLatch(1)
        var result: OrderChange? = null
        val unregister = registerOrderChanges(context) { result = it; received.countDown() }
        try {
            val change = OrderChange(12, 34, OrderChangeKind.STATUS_CHANGED)
            BroadcastOrderChangePublisher(context).publish(change)
            assertTrue(received.await(5, TimeUnit.SECONDS))
            assertEquals(change, result)
        } finally { unregister() }
    }
}
