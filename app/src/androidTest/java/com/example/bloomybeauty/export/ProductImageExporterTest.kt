package com.example.bloomybeauty.export

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.catalog.CatalogRepository
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.Instant

class ProductImageExporterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var auth: AuthRepository
    private lateinit var exporter: ProductImageExporter
    private var viewer = 0L
    private var now = Instant.parse("2026-11-16T12:00:00Z").toEpochMilli()
    @Before fun setup() = runBlocking {
        context.deleteDatabase(DB); helper = AuthDatabaseHelper(context, DB)
        exporter = ProductImageExporter(context, helper) { now }; exporter.clear()
        auth = AuthRepository(helper, onSessionChanged = { exporter.clear() })
        viewer = (auth.register("KHÁCH KHÔNG ĐƯỢC ĐƯA VÀO ẢNH", "private@share.test", PASSWORD, PASSWORD) as AuthResult.Success).user.id
    }
    @After fun cleanup() { exporter.clear(); helper.close(); context.deleteDatabase(DB) }
    private suspend fun denied(error: ProductImageError, block: suspend () -> Unit) {
        try { block(); fail("Expected $error") } catch (e: ProductImageException) { assertEquals(error, e.error) }
    }
    @Test fun pngIsReadableViaNarrowProviderAndShareIntentGrantsOnlyReadAccess(): Unit = runBlocking {
        val artifact = exporter.export(viewer, "cleanser")
        assertEquals("content", artifact.uri.scheme)
        assertEquals("${context.packageName}.product-images", artifact.uri.authority)
        assertEquals("image/png", context.contentResolver.getType(artifact.uri))
        val image = context.contentResolver.openInputStream(artifact.uri)!!.use { BitmapFactory.decodeStream(it) }!!
        assertEquals(1080, image.width); assertTrue(image.height in 1000..2400)
        assertTrue(image.getPixel(0,0) != image.getPixel(64,160)); image.recycle()
        val intent = exporter.shareIntent(artifact)
        assertEquals(Intent.ACTION_SEND, intent.action); assertEquals("image/png", intent.type)
        assertEquals(artifact.uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(artifact.uri, intent.clipData!!.getItemAt(0).uri)
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, intent.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
        assertFalse(intent.getStringExtra(Intent.EXTRA_TEXT)!!.contains("private@share.test"))
        val provider = context.packageManager.resolveContentProvider(artifact.uri.authority!!, 0)!!
        assertFalse(provider.exported); assertTrue(provider.grantUriPermissions)
        artifact.file.copyTo(File(evidence(), "01-cleanser.png"), overwrite = true)
    }
    @Test fun missingInactiveAndOldSessionCannotExportButSoldOutCanBeShared(): Unit = runBlocking {
        denied(ProductImageError.UNAVAILABLE) { exporter.export(viewer, "missing") }
        helper.writableDatabase.execSQL("UPDATE products SET active=0 WHERE id='cleanser'")
        denied(ProductImageError.UNAVAILABLE) { exporter.export(viewer, "cleanser") }
        helper.writableDatabase.execSQL("UPDATE products SET active=1,stock=0 WHERE id='cleanser'")
        val soldOut = exporter.export(viewer, "cleanser")
        assertEquals(0, soldOut.product.stock)
        soldOut.file.copyTo(File(evidence(), "02-sold-out.png"), overwrite = true)
        auth.logout()
        denied(ProductImageError.SESSION) { exporter.export(viewer, "cleanser") }
        assertFalse(soldOut.file.exists())
    }
    @Test fun priceChangeAfterPreviewRejectsStalePosterAndDeletesIt(): Unit = runBlocking {
        val old = exporter.export(viewer, "cleanser")
        helper.writableDatabase.execSQL("UPDATE products SET price_vnd=200000,revision=revision+1 WHERE id='cleanser'")
        denied(ProductImageError.CHANGED) { exporter.shareIntent(old) }
        assertFalse(old.file.exists())
        val fresh = exporter.export(viewer, "cleanser")
        assertEquals(200000L, fresh.product.priceVnd)
        assertNotNull(exporter.shareIntent(fresh))
    }
    @Test fun switchingAccountBetweenPreviewAndShareRejectsOldArtifact(): Unit = runBlocking {
        val old = exporter.export(viewer, "cleanser")
        auth.register("Khách khác", "other@share.test", PASSWORD, PASSWORD)
        // Remove the session directly below to exercise the gate independently of the clear callback.
        val current = (auth.login("private@share.test", PASSWORD) as AuthResult.Success).user.id
        val stale = exporter.export(current, "cleanser")
        helper.writableDatabase.execSQL("DELETE FROM auth_session")
        denied(ProductImageError.SESSION) { exporter.shareIntent(stale) }
        assertFalse(stale.file.exists()); assertFalse(old.file.exists())
    }
    @Test fun uniqueFilesExpireAndUnsharedPreviewCanBeDiscarded(): Unit = runBlocking {
        val first = exporter.export(viewer, "cleanser"); val second = exporter.export(viewer, "cleanser")
        assertNotEquals(first.uri, second.uri)
        exporter.discard(second); assertFalse(second.file.exists())
        now += 86_400_000L
        denied(ProductImageError.EXPIRED) { exporter.shareIntent(first) }
        assertFalse(first.file.exists())
        val third = exporter.export(viewer, "cleanser")
        now += 86_400_000L; exporter.cleanupExpired()
        assertFalse(third.file.exists())
    }
    @Test fun providerAndInvalidArtifactCannotExposeOrDeleteSiblingCacheFiles(): Unit = runBlocking {
        val outside = File(context.cacheDir, "share-private-test.txt").apply { writeText("PRIVATE") }
        try {
            try { FileProvider.getUriForFile(context, "${context.packageName}.product-images", outside); fail("Sibling exposed") }
            catch (_: IllegalArgumentException) { }
            val product = CatalogRepository(helper).load(viewer).products.first()
            val forged = ProductImageArtifact(outside, Uri.EMPTY, viewer, product, now)
            try { exporter.shareIntent(forged); fail("Unsafe file accepted") } catch (_: IllegalArgumentException) { }
            assertTrue(outside.exists())
        } finally { outside.delete() }
    }
    @Test fun veryLongVietnameseContentAndUnknownImageStayBoundedAndCanRenderAgain(): Unit = runBlocking {
        val product = CatalogRepository(helper).load(viewer).products.first().copy(
            name = "Sản phẩm chăm sóc tiếng Việt ".repeat(200),
            description = "Dưỡng ẩm và làm sạch, không đưa thông tin tài khoản vào ảnh. ".repeat(300), imageKey = "unknown")
        val file = File(evidence(), "03-long-vietnamese.png")
        file.outputStream().use { ProductImageRenderer.write(context, product, now, it) }
        val image = BitmapFactory.decodeFile(file.path)!!
        assertEquals(1080, image.width); assertTrue(image.height < 2600); image.recycle()
        assertTrue(exporter.export(viewer, "cleanser").file.length() > 1000)
    }
    @Test fun failingOutputDoesNotPreventNextSuccessfulExport(): Unit = runBlocking {
        val product = CatalogRepository(helper).load(viewer).products.first()
        try {
            ProductImageRenderer.write(context, product, now, object : OutputStream() {
                override fun write(value: Int) { throw IOException("Failure fixture") }
            }); fail("Expected IOException")
        } catch (_: IOException) { }
        assertTrue(exporter.export(viewer, "cleanser").file.isFile)
    }
    private fun evidence() = File(context.getExternalFilesDir(null), "pixel6-product-share").apply { mkdirs() }
    companion object { private const val DB = "product-share-exporter-test.db"; private const val PASSWORD = "ProductShare123!" }
}
