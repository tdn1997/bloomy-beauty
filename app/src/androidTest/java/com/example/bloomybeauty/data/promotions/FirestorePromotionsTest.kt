package com.example.bloomybeauty.data.promotions

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.TimeUnit

/** All writes go to the local emulator under a demo project, never production. */
@RunWith(AndroidJUnit4::class)
class FirestorePromotionsTest {
    private lateinit var app: FirebaseApp
    private lateinit var firestore: FirebaseFirestore
    private lateinit var repository: FirestorePromotionsRepository

    @Before fun setup() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firestoreEmulator") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        app = FirebaseApp.initializeApp(context, FirebaseOptions.Builder()
            .setProjectId(PROJECT).setApplicationId("1:123:android:promotions")
            .setApiKey("emulator-only-api-key").build(), "promotions-${UUID.randomUUID()}")
        firestore = FirebaseFirestore.getInstance(app)
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
        firestore.useEmulator("10.0.2.2", 8080)
        repository = FirestorePromotionsRepository(firestore)
        request("DELETE", "/emulator/v1/projects/$PROJECT/databases/(default)/documents")
    }

    @After fun cleanup() {
        if (::firestore.isInitialized) Tasks.await(firestore.terminate(), 10, TimeUnit.SECONDS)
        if (::app.isInitialized) app.delete()
    }

    @Test fun publishedChangesUnpublishingAndDeletionReachListener() = runBlocking<Unit> {
        val events = Channel<PromotionsFeed>(Channel.UNLIMITED)
        val subscription = launch(Dispatchers.Default) { repository.observe().collect { events.send(it) } }
        try {
            events.awaitFeed { !it.loading && !it.fromCache }
            put("flash_sales", "sale", sale("Giờ vàng"))
            assertEquals("Giờ vàng", events.awaitFeed { it.flashSales.size == 1 }.flashSales.single().title)
            put("flash_sales", "sale", sale("Giá mới", price = 129_000))
            val updated = events.awaitFeed { it.flashSales.singleOrNull()?.salePriceVnd == 129_000L }
            assertEquals("Giá mới", updated.flashSales.single().title)
            put("coupon_campaigns", "coupon", coupon())
            assertEquals("BLOOMY20", events.awaitFeed { it.coupons.size == 1 }.coupons.single().code)
            put("flash_sales", "sale", sale("Ẩn", published = false))
            events.awaitFeed { it.flashSales.isEmpty() && it.coupons.size == 1 }
            request("DELETE", document("coupon_campaigns", "coupon"))
            events.awaitFeed { it.flashSales.isEmpty() && it.coupons.isEmpty() && !it.fromCache }
        } finally { subscription.cancelAndJoin() }
    }

    @Test fun malformedDocumentDoesNotHideOtherCampaigns() = runBlocking<Unit> {
        val invalid = sale("Sai timestamp")
        invalid.getJSONObject("fields").put("startsAt", JSONObject().put("integerValue", "1000"))
        put("flash_sales", "bad", invalid)
        put("flash_sales", "good", sale("Hợp lệ"))
        put("coupon_campaigns", "coupon", coupon())
        val events = Channel<PromotionsFeed>(Channel.UNLIMITED)
        val subscription = launch(Dispatchers.Default) { repository.observe().collect { events.send(it) } }
        try {
            val feed = events.awaitFeed { !it.loading && !it.fromCache }
            assertEquals(listOf("good"), feed.flashSales.map { it.id })
            assertEquals(1, feed.coupons.size)
            assertNull(feed.error)
        } finally { subscription.cancelAndJoin() }
    }

    @Test fun cacheIsMarkedAndReconnectDeliversNewPrice() = runBlocking<Unit> {
        put("flash_sales", "sale", sale("Giờ vàng"))
        val events = Channel<PromotionsFeed>(Channel.UNLIMITED)
        val subscription = launch(Dispatchers.Default) { repository.observe().collect { events.send(it) } }
        try {
            events.awaitFeed { !it.fromCache && it.flashSales.size == 1 }
            Tasks.await(firestore.disableNetwork(), 10, TimeUnit.SECONDS)
            val cached = events.awaitFeed { it.fromCache && it.flashSales.size == 1 }
            assertEquals(149_000L, cached.flashSales.single().salePriceVnd)
            put("flash_sales", "sale", sale("Cập nhật", price = 119_000))
            Tasks.await(firestore.enableNetwork(), 10, TimeUnit.SECONDS)
            events.awaitFeed { !it.fromCache && it.flashSales.singleOrNull()?.salePriceVnd == 119_000L }
        } finally { subscription.cancelAndJoin() }
    }

    private suspend fun Channel<PromotionsFeed>.awaitFeed(predicate: (PromotionsFeed) -> Boolean): PromotionsFeed =
        withTimeout(15_000) {
            var feed = receive()
            while (!predicate(feed)) feed = receive()
            assertNull(feed.error)
            feed
        }

    private fun sale(title: String, published: Boolean = true, price: Long = 149_000): JSONObject = JSONObject().put("fields",
        common(title, published).put("productId", string("cleanser"))
            .put("salePriceVnd", integer(price)))

    private fun coupon(): JSONObject = JSONObject().put("fields", common("Mã mới", true)
        .put("code", string("BLOOMY20")).put("discountType", string("percent"))
        .put("discountValue", integer(20)).put("minOrderVnd", integer(200_000))
        .put("maxDiscountVnd", integer(50_000)))

    private fun common(title: String, published: Boolean): JSONObject = JSONObject()
        .put("title", string(title)).put("published", JSONObject().put("booleanValue", published))
        .put("startsAt", JSONObject().put("timestampValue", "2026-01-01T00:00:00Z"))
        .put("endsAt", JSONObject().put("timestampValue", "2030-01-01T00:00:00Z"))
    private fun string(value: String) = JSONObject().put("stringValue", value)
    private fun integer(value: Long) = JSONObject().put("integerValue", value.toString())
    private fun document(collection: String, id: String) = "/v1/projects/$PROJECT/databases/(default)/documents/$collection/$id"
    private fun put(collection: String, id: String, document: JSONObject) { request("PATCH", document(collection, id), document) }

    private fun request(method: String, path: String, body: JSONObject? = null) {
        val connection = URL("http://10.0.2.2:8080$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            // Owner token bypass is supported only by the local emulator.
            connection.setRequestProperty("Authorization", "Bearer owner")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            check(connection.responseCode in 200..299) {
                "Emulator fixture failed: ${connection.responseCode}: ${connection.errorStream?.bufferedReader()?.readText()}"
            }
            connection.inputStream.use { it.readBytes() }
        } finally { connection.disconnect() }
    }

    companion object { private const val PROJECT = "demo-bloomy-beauty" }
}
