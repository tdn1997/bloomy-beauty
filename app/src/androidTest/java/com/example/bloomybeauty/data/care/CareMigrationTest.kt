package com.example.bloomybeauty.data.care

import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.*
import com.example.bloomybeauty.data.order.OrderRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CareMigrationTest {
    @Test fun versionFiveUpgradePreservesSessionCartOrdersAndStock() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "care-migration-test.db"
        context.deleteDatabase(name)
        var helper = AuthDatabaseHelper(context, name)
        try {
            val password = "CareTest123!"
            val id = (AuthRepository(helper).register("Khách", "migration@example.com", password, password) as AuthResult.Success).user.id
            val cart = CartRepository(helper)
            val orders = OrderRepository(helper)
            cart.add(id, "cleanser")
            cart.saveShipping(id, ShippingDetails("Khách thử nghiệm", "0912345678", "123 Võ Văn Ngân, Thủ Đức, TP Hồ Chí Minh"))
            val order = orders.create(id, orders.prepare(id))
            cart.add(id, "lip_balm")
            helper.writableDatabase.apply { execSQL("DROP TABLE care_routines"); version = 5 }
            helper.close()
            helper = AuthDatabaseHelper(context, name)
            assertEquals(6, helper.readableDatabase.version)
            assertEquals(id, AuthRepository(helper).restoreSession()!!.id)
            assertEquals(order.id, OrderRepository(helper).list(id).single().id)
            assertEquals("lip_balm", CartRepository(helper).load(id).items.single().product.id)
            helper.readableDatabase.rawQuery("SELECT stock FROM products WHERE id='cleanser'", null).use { it.moveToFirst(); assertEquals(29, it.getInt(0)) }
            helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM care_routines", null).use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            helper.readableDatabase.rawQuery("PRAGMA foreign_key_check", null).use { assertEquals(0, it.count) }
        } finally { helper.close(); context.deleteDatabase(name) }
    }
}
