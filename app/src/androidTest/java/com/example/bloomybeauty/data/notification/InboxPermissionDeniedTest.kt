package com.example.bloomybeauty.data.notification

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.OrderRepository
import com.example.bloomybeauty.events.*
import com.example.bloomybeauty.notifications.OrderNotifications
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue

/** Host revokes permission before instrumentation and restores it afterwards; no runtime revoke kills this process. */
class InboxPermissionDeniedTest {
    @Test fun permissionDenialPreservesDurableHistoryUnreadAndNavigation(): Unit=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_DENIED)
        context.deleteDatabase(DB)
        val helper=AuthDatabaseHelper(context,DB)
        val notifications=OrderNotifications(context,helper)
        try {
            notifications.clear()
            val auth=AuthRepository(helper)
            val owner=(auth.register("Khách tắt thông báo","denied@inbox.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
            val cart=CartRepository(helper)
            val publisher=OrderChangePublisher {change -> notifications.show(change)}
            val orders=OrderRepository(helper,publisher)
            cart.add(owner,"cleanser");cart.saveShipping(owner,ShippingDetails("Nguyễn Ánh Hương","0912345678","123 Nguyễn Huệ, Thành phố Hồ Chí Minh"))
            val request=orders.prepare(owner);val order=orders.create(owner,request)
            orders.create(owner,request)
            val inbox=NotificationRepository(helper);val page=inbox.page(owner)
            assertEquals(1L,page.unread);assertEquals(1,page.items.size)
            assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.none {it.tag?.startsWith("order-inbox:")==true})
            assertEquals(order.id,inbox.open(owner,page.items.single().id).id)
            assertEquals(0L,inbox.page(owner).unread);assertEquals("PENDING",orders.detail(owner,order.id)!!.status)
        } finally {notifications.clear();helper.close();context.deleteDatabase(DB)}
    }
    companion object {private const val DB="inbox-permission-denied-test.db";private const val PASSWORD="InboxTest123!"}
}
