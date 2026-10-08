package com.example.bloomybeauty.data.admin

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.catalog.Category
import com.example.bloomybeauty.data.catalog.Product
import com.example.bloomybeauty.data.order.CustomerOrder
import com.example.bloomybeauty.data.order.OrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

val demoImageKeys = listOf("product_cleanser", "product_micellar", "product_serum", "product_toner", "product_body_scrub", "product_shampoo", "product_lip_scrub", "product_lip_balm")
enum class AdminError { PERMISSION, INVALID_PRODUCT, CONFLICT, NOT_FOUND, INVALID_TRANSITION, STOCK_OVERFLOW, STORAGE }
class AdminException(val error: AdminError) : Exception(error.name)
data class OrderEvent(val actor: String, val from: String?, val to: String, val time: Long)
data class AdminSnapshot(val products: List<Product>, val categories: List<Category>, val orders: List<CustomerOrder>, val events: Map<Long, List<OrderEvent>>)

object AdminRules {
    val transitions = mapOf("PENDING" to listOf("CONFIRMED", "CANCELLED"), "CONFIRMED" to listOf("SHIPPING", "CANCELLED"), "SHIPPING" to listOf("DELIVERED"))
    fun valid(p: Product): Boolean = p.name.trim().length in 2..160 && p.brand.trim().length in 1..80 &&
        p.volume.trim().length in 1..80 && p.priceVnd in 1..1_000_000_000L && p.stock >= 0 && p.imageKey in demoImageKeys &&
        p.description.trim().length in 1..4000 && p.ingredients.trim().length in 1..4000 && p.usage.trim().length in 1..4000 &&
        (p.sourceUrl.isBlank() || (p.sourceUrl.length <= 1000 && runCatching { java.net.URI(p.sourceUrl).let { it.scheme == "https" && !it.host.isNullOrBlank() } }.getOrDefault(false)))
}

class AdminRepository(private val helper: AuthDatabaseHelper) {
    private val orders = OrderRepository(helper)
    private suspend fun <T> authorized(userId: Long, block: (SQLiteDatabase) -> T): T = withContext(Dispatchers.IO) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT 1 FROM auth_session s JOIN users u ON u._id=s.user_id WHERE u._id=? AND u.role='ADMIN'", arrayOf(userId.toString())).use {
                if (!it.moveToFirst()) throw AdminException(AdminError.PERMISSION)
            }
            val result = block(db); db.setTransactionSuccessful(); result
        } finally { db.endTransaction() }
    }
    suspend fun load(userId: Long): AdminSnapshot = authorized(userId) { db ->
        val products = db.rawQuery("SELECT * FROM products ORDER BY sort_order, id", null).use { c -> buildList { while(c.moveToNext()) add(c.product()) } }
        val categories = db.rawQuery("SELECT id,name FROM categories ORDER BY sort_order", null).use { c -> buildList { while(c.moveToNext()) add(Category(c.getString(0), c.getString(1))) } }
        val allOrders = db.rawQuery("SELECT * FROM orders ORDER BY created_at DESC, _id DESC", null).use { c -> buildList { while(c.moveToNext()) add(orders.readOrder(db, c)) } }
        val events = mutableMapOf<Long, MutableList<OrderEvent>>()
        db.rawQuery("SELECT * FROM order_events ORDER BY changed_at, _id", null).use { c -> while(c.moveToNext()) {
            events.getOrPut(c.long("order_id")) { mutableListOf() }.add(OrderEvent(c.text("actor_name"), c.getString(c.getColumnIndexOrThrow("from_status")), c.text("to_status"), c.long("changed_at")))
        } }
        AdminSnapshot(products, categories, allOrders, events)
    }
    suspend fun save(userId: Long, product: Product, isNew: Boolean): String = authorized(userId) { db ->
        if (!AdminRules.valid(product)) throw AdminException(AdminError.INVALID_PRODUCT)
        db.rawQuery("SELECT 1 FROM categories WHERE id=?", arrayOf(product.categoryId)).use { if(!it.moveToFirst()) throw AdminException(AdminError.INVALID_PRODUCT) }
        val id = if (isNew) UUID.randomUUID().toString() else product.id
        val v = ContentValues().apply {
            put("name", product.name.trim()); put("brand", product.brand.trim()); put("category_id", product.categoryId); put("volume", product.volume.trim())
            put("price_vnd", product.priceVnd); put("stock", product.stock); put("active", if(product.active) 1 else 0); put("image_key", product.imageKey)
            put("description", product.description.trim()); put("ingredients", product.ingredients.trim()); put("usage", product.usage.trim()); put("source_url", product.sourceUrl.trim())
        }
        if(isNew) {
            v.put("id", id); v.put("sort_order", db.rawQuery("SELECT COALESCE(MAX(sort_order),0)+1 FROM products", null).use { it.moveToFirst(); it.getInt(0) })
            db.insertOrThrow("products", null, v)
        } else {
            v.put("revision", Math.addExact(product.revision, 1L))
            if(db.update("products", v, "id=? AND revision=?", arrayOf(id, product.revision.toString())) != 1) throw AdminException(AdminError.CONFLICT)
        }
        id
    }
    suspend fun transition(userId: Long, orderId: Long, expected: String, target: String) = authorized(userId) { db ->
        val order = db.rawQuery("SELECT * FROM orders WHERE _id=?", arrayOf(orderId.toString())).use { c ->
            if(!c.moveToFirst()) throw AdminException(AdminError.NOT_FOUND); orders.readOrder(db,c)
        }
        if(target !in (AdminRules.transitions[expected] ?: emptyList())) throw AdminException(AdminError.INVALID_TRANSITION)
        if(order.status == target) return@authorized // Repeated request: no duplicate stock or audit.
        if(order.status != expected) throw AdminException(AdminError.CONFLICT)
        if(db.update("orders", ContentValues().apply { put("status",target) }, "_id=? AND status=?", arrayOf(orderId.toString(),expected)) != 1) throw AdminException(AdminError.CONFLICT)
        if(target == "CANCELLED") order.lines.forEach { line ->
            val stock = db.rawQuery("SELECT stock FROM products WHERE id=?", arrayOf(line.productId)).use { if(!it.moveToFirst()) throw AdminException(AdminError.NOT_FOUND); it.getInt(0) }
            val restored = try { Math.addExact(stock, line.quantity) } catch (_: ArithmeticException) { throw AdminException(AdminError.STOCK_OVERFLOW) }
            db.execSQL("UPDATE products SET stock=?, revision=revision+1 WHERE id=?", arrayOf(restored,line.productId))
        }
        AdminSchema.record(db,orderId,userId,expected,target)
    }
    private fun Cursor.text(key: String) = getString(getColumnIndexOrThrow(key))
    private fun Cursor.long(key: String) = getLong(getColumnIndexOrThrow(key))
    private fun Cursor.product() = Product(text("id"),text("name"),text("category_id"),text("volume"),long("price_vnd"),text("image_key"),text("description"),text("ingredients"),text("usage"),text("source_url"),text("brand"),getInt(getColumnIndexOrThrow("stock")),getInt(getColumnIndexOrThrow("active"))==1,long("revision"))
}
