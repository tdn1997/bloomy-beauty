package com.example.bloomybeauty.export

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloomybeauty.data.auth.*
import com.example.bloomybeauty.data.cart.ShippingDetails
import com.example.bloomybeauty.data.order.*
import com.example.bloomybeauty.fixtures.StatisticsFixture
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.io.IOException
import java.io.OutputStream

class OrderPdfExporterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: AuthDatabaseHelper
    private lateinit var auth: AuthRepository
    private lateinit var exporter: OrderPdfExporter
    private var owner = 0L
    private var other = 0L
    private var id = 0L
    @Before fun setup(): Unit = runBlocking {
        context.deleteDatabase(DB);helper = AuthDatabaseHelper(context,DB);auth = AuthRepository(helper)
        owner = (auth.register("Nguyễn Thị Ánh","owner@pdf.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        other = (auth.register("Người khác","other@pdf.test",PASSWORD,PASSWORD) as AuthResult.Success).user.id
        auth.login("owner@pdf.test",PASSWORD)
        id = StatisticsFixture.order(helper.writableDatabase,owner,"CANCELLED",1794106800000,null,200_000,20_000,30_000)
        exporter = OrderPdfExporter(context,helper);exporter.clear()
    }
    @After fun cleanup() {exporter.clear();helper.close();context.deleteDatabase(DB)}
    private suspend fun denied(block: suspend () -> Unit) {try {block();fail("Expected access denial")} catch(_: PdfAccessException) {}}
    @Test fun ownerSnapshotSurvivesCatalogChangesAndHasReadableContentUri(): Unit = runBlocking {
        helper.writableDatabase.execSQL("UPDATE products SET name='CURRENT CATALOG MUST NOT APPEAR',price_vnd=1 WHERE id='cleanser'")
        val artifact = exporter.export(owner,id)
        assertEquals("content",artifact.uri.scheme);assertEquals(context.packageName+".receipts",artifact.uri.authority)
        context.contentResolver.openFileDescriptor(artifact.uri,"r")!!.use {descriptor ->
            val renderer = PdfRenderer(descriptor)
            try {assertEquals(1,renderer.pageCount);renderer.openPage(0).use {page ->
                val bitmap = Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                assertTrue((0 until bitmap.height step 4).any {y -> (0 until bitmap.width step 4).any {x -> bitmap.getPixel(x,y) != Color.WHITE}})
                bitmap.recycle()
            }} finally {renderer.close()}
        }
        artifact.file.copyTo(File(evidence(),"01-snapshot-annule-coupon.pdf"),true)
        assertEquals("CANCELLED",OrderRepository(helper).detail(owner,id)!!.status)
    }
    @Test fun customerCannotExportAnotherOwnerButCurrentAdminCan(): Unit = runBlocking {
        auth.login("other@pdf.test",PASSWORD)
        denied {exporter.export(other,id)};denied {exporter.export(owner,id)}
        helper.writableDatabase.execSQL("UPDATE users SET role='ADMIN' WHERE _id=?",arrayOf(other))
        val pdf = exporter.export(other,id);assertTrue(pdf.file.isFile)
        helper.writableDatabase.execSQL("UPDATE users SET role='CUSTOMER' WHERE _id=?",arrayOf(other))
        denied {exporter.launch(pdf,context,true)};assertFalse(pdf.file.exists())
    }
    @Test fun logoutBetweenExportAndLaunchRejectsGrantAndRemovesFile(): Unit = runBlocking {
        val pdf = exporter.export(owner,id);auth.logout()
        denied {exporter.launch(pdf,context,true)};assertFalse(pdf.file.exists())
    }
    @Test fun filesAreUniqueExpiredFilesAreRemovedAndProviderCannotExposeSibling(): Unit = runBlocking {
        val first = exporter.export(owner,id);val second = exporter.export(owner,id)
        assertNotEquals(first.uri,second.uri)
        assertTrue(first.file.setLastModified(1));exporter.cleanupExpired(86_400_002)
        assertFalse(first.file.exists());assertTrue(second.file.exists())
        val outside = File(context.cacheDir,"private-test.txt").apply {writeText("private")}
        try {FileProvider.getUriForFile(context,context.packageName+".receipts",outside);fail("Outside cache subtree exposed")}
        catch(_: IllegalArgumentException) {} finally {outside.delete()}
        exporter.clear();assertFalse(second.file.exists())
    }
    @Test fun missingOrderDoesNotLeavePartialDocument(): Unit = runBlocking {
        denied {exporter.export(owner,Long.MAX_VALUE)}
        assertTrue(File(context.cacheDir,"order-pdfs").listFiles().orEmpty().isEmpty())
    }
    @Test fun vietnameseVeryLongAddressAndHundredProductsUseMultiplePages(): Unit = runBlocking {
        val lines = (1..100).map {OrderLine("sku-$it","Sản phẩm dưỡng ẩm tiếng Việt số $it — Nguyễn Ánh Hương","Dung tích 100 ml","",2,150_000)}
        val order = CustomerOrder(999,"long-fixture",1794106800000,"DELIVERED",
            ShippingDetails("Nguyễn Thị Ánh Hương","0912345678",("Đường Nguyễn Huệ, phường Bến Nghé, Thành phố Hồ Chí Minh. ").repeat(100)),
            30_000_000,0,29_950_000,lines,50_000,"DEMO50K","{}")
        val file = File(evidence(),"02-vietnamese-100-products-long-address.pdf")
        file.outputStream().use {OrderPdfRenderer.write(order,it)}
        ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {descriptor ->
            val pdf = PdfRenderer(descriptor)
            try {assertTrue("Expected multiple pages, got ${pdf.pageCount}",pdf.pageCount >= 5)} finally {pdf.close()}
        }
    }
    @Test fun failedOutputCanBeFollowedBySuccessfulExport(): Unit = runBlocking {
        val order = OrderRepository(helper).detail(owner,id)!!
        try {OrderPdfRenderer.write(order,object: OutputStream() {override fun write(value: Int) {throw IOException("full fixture")}});fail("Expected IO error")}
        catch(_: IOException) {}
        val artifact = exporter.export(owner,id);assertTrue(artifact.file.length() > 1000)
    }
    @Test fun zeroDiscountLegacyOrderIsRenderedWithoutInventedCoupon(): Unit = runBlocking {
        val legacy = StatisticsFixture.order(helper.writableDatabase,owner,"PENDING",1794106800000,null,100_000,0,30_000)
        exporter.export(owner,legacy).file.copyTo(File(evidence(),"03-legacy-zero-discount.pdf"),true)
        assertNull(OrderRepository(helper).detail(owner,legacy)!!.couponCode)
    }
    private fun evidence() = File(context.getExternalFilesDir(null),"pixel6-pdf-qa").apply {mkdirs()}
    companion object {private const val DB = "pdf-exporter-test.db";private const val PASSWORD = "PdfTest123!"}
}
