package com.example.bloomybeauty.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.order.OrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class PdfAccessException : Exception("Order is unavailable for this session")
class PdfArtifact internal constructor(internal val file: File, val uri: Uri, internal val viewer: Long, internal val orderId: Long)
class OrderPdfExporter(context: Context, private val helper: AuthDatabaseHelper) {
    private val context = context.applicationContext
    private val folder get() = File(context.cacheDir,"order-pdfs")
    private val authority get() = "${context.packageName}.receipts"
    private fun read(viewer: Long, id: Long) = helper.readableDatabase.let {db ->
        db.beginTransaction()
        try {
            val allowed = db.rawQuery("""SELECT o.* FROM orders o JOIN auth_session s ON s._id=1
                JOIN users u ON u._id=s.user_id WHERE u._id=? AND o._id=? AND (o.user_id=u._id OR u.role='ADMIN')""",arrayOf("$viewer","$id")).use {
                if(!it.moveToFirst()) throw PdfAccessException()
                OrderRepository(helper).readOrder(db,it)
            }
            db.setTransactionSuccessful();allowed
        } finally {db.endTransaction()}
    }
    suspend fun export(viewer: Long, orderId: Long): PdfArtifact {
        var file: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val order = read(viewer,orderId)
                cleanupExpired()
                check(folder.isDirectory || folder.mkdirs()) {"Cannot create PDF cache"}
                val target = File(folder,"u${viewer}_${order.code}_${UUID.randomUUID()}.pdf").also {file = it}
                target.outputStream().use {OrderPdfRenderer.write(order,it)}
                read(viewer,orderId) // A logout/role change while rendering must not release the document.
                PdfArtifact(target,FileProvider.getUriForFile(context,authority,target),viewer,orderId)
            }
        } catch(error: Throwable) {file?.delete();throw error}
    }
    suspend fun launch(artifact: PdfArtifact, activityContext: Context, share: Boolean) {
        try {
            withContext(Dispatchers.IO) {
                require(artifact.file.parentFile?.canonicalFile == folder.canonicalFile && artifact.file.isFile)
                read(artifact.viewer,artifact.orderId)
            }
            withContext(Dispatchers.Main.immediate) {
                val intent = Intent(if(share) Intent.ACTION_SEND else Intent.ACTION_VIEW).apply {
                    if(share) {type = "application/pdf";putExtra(Intent.EXTRA_STREAM,artifact.uri)}
                    else setDataAndType(artifact.uri,"application/pdf")
                    clipData = ClipData.newRawUri("Phiếu đơn hàng — bản demo",artifact.uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activityContext.startActivity(Intent.createChooser(intent,if(share) "Chia sẻ phiếu đơn demo" else "Mở phiếu đơn demo"))
            }
        } catch(error: Throwable) {remove(artifact.file);throw error}
    }
    private fun remove(file: File) {
        context.revokeUriPermission(FileProvider.getUriForFile(context,authority,file),Intent.FLAG_GRANT_READ_URI_PERMISSION)
        file.delete()
    }
    fun cleanupExpired(now: Long = System.currentTimeMillis()) {
        folder.listFiles()?.filter {it.isFile && now-it.lastModified() >= 24*60*60*1000L}?.forEach(::remove)
    }
    fun clear() {folder.listFiles()?.filter {it.isFile}?.forEach(::remove)}
}
