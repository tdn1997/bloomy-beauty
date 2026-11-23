package com.example.bloomybeauty.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.bloomybeauty.data.auth.AuthDatabaseHelper
import com.example.bloomybeauty.data.auth.SessionException
import com.example.bloomybeauty.data.catalog.CatalogRepository
import com.example.bloomybeauty.data.catalog.Product
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class ProductImageError { SESSION, UNAVAILABLE, CHANGED, EXPIRED }
class ProductImageException(val error: ProductImageError) : Exception(error.name)
class ProductImageArtifact internal constructor(
    internal val file: File, val uri: Uri, internal val viewer: Long,
    internal val product: Product, internal val createdAt: Long,
) { val productName: String get() = product.name }

class ProductImageExporter(context: Context, helper: AuthDatabaseHelper,
    private val clock: () -> Long = System::currentTimeMillis) {
    private val context = context.applicationContext
    private val catalog = CatalogRepository(helper)
    private val folder get() = File(context.cacheDir, "product-images")
    private val authority get() = "${context.packageName}.product-images"

    private suspend fun read(viewer: Long, id: String): Product {
        val data = try { catalog.load(viewer) }
        catch (_: SessionException) { throw ProductImageException(ProductImageError.SESSION) }
        return data.products.firstOrNull { it.id == id } ?: throw ProductImageException(ProductImageError.UNAVAILABLE)
    }

    suspend fun export(viewer: Long, productId: String): ProductImageArtifact {
        var pending: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val product = read(viewer, productId)
                val now = clock()
                cleanupExpired(now)
                check(folder.isDirectory || folder.mkdirs())
                val file = File(folder, "${UUID.randomUUID()}.png").also { pending = it }
                file.outputStream().use { ProductImageRenderer.write(context, product, now, it) }
                check(file.setLastModified(now))
                if (read(viewer, productId) != product) throw ProductImageException(ProductImageError.CHANGED)
                ProductImageArtifact(file, FileProvider.getUriForFile(context, authority, file), viewer, product, now)
            }
        } catch (e: Throwable) { pending?.let(::remove); throw e }
    }

    suspend fun shareIntent(artifact: ProductImageArtifact): Intent {
        try {
            return withContext(Dispatchers.IO) {
                require(artifact.file.parentFile?.canonicalFile == folder.canonicalFile && artifact.file.isFile)
                if (clock() - artifact.createdAt >= MAX_AGE) throw ProductImageException(ProductImageError.EXPIRED)
                if (read(artifact.viewer, artifact.product.id) != artifact.product) throw ProductImageException(ProductImageError.CHANGED)
                val uri = FileProvider.getUriForFile(context, authority, artifact.file)
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "${artifact.productName} — Bloomy Beauty (demo)")
                    putExtra(Intent.EXTRA_TITLE, artifact.productName)
                    clipData = ClipData.newUri(context.contentResolver, "Ảnh sản phẩm", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        } catch (e: Throwable) { discard(artifact); throw e }
    }

    fun discard(artifact: ProductImageArtifact) = remove(artifact.file)
    private fun remove(file: File) {
        if (file.parentFile?.canonicalFile != folder.canonicalFile) return
        try { context.revokeUriPermission(FileProvider.getUriForFile(context, authority, file), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        finally { file.delete() }
    }
    fun cleanupExpired(now: Long = clock()) {
        folder.listFiles().orEmpty().filter { it.isFile && now - it.lastModified() >= MAX_AGE }.forEach(::remove)
    }
    fun clear() { folder.listFiles().orEmpty().filter { it.isFile }.forEach(::remove) }
    companion object { private const val MAX_AGE = 86_400_000L }
}

/** Only the product image cache subtree is exposed, using temporary read grants. */
class ProductImageFileProvider : FileProvider()
