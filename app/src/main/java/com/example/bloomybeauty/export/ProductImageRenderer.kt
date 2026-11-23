package com.example.bloomybeauty.export

import android.content.Context
import android.graphics.*
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.example.bloomybeauty.data.catalog.Product
import com.example.bloomybeauty.feature.home.formatPrice
import com.example.bloomybeauty.feature.home.productImage
import java.io.IOException
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A bounded, public product card. No account, cart, address or screen contents are captured. */
internal object ProductImageRenderer {
    const val WIDTH = 1080
    private const val TEXT_WIDTH = 952

    fun write(context: Context, product: Product, createdAt: Long, output: OutputStream) {
        val ink = Color.rgb(54, 44, 49)
        val accent = Color.rgb(153, 64, 101)
        fun layout(text: String, size: Float, bold: Boolean = false, lines: Int = 6, color: Int = ink): StaticLayout {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size; this.color = color
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            val value = text.take(2000)
            return StaticLayout.Builder.obtain(value, 0, value.length, paint, TEXT_WIDTH)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false)
                .setLineSpacing(6f, 1f).setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build()
        }
        val date = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
        val blocks = listOf(
            layout(product.name, 52f, true, 4) to 20,
            layout("${product.brand} · ${product.volume}", 30f, lines = 3) to 24,
            layout(formatPrice(product.priceVnd), 68f, true, 2, accent) to 20,
            layout(if (product.stock > 0) "Còn hàng tại thời điểm tạo ảnh" else "Hết hàng tại thời điểm tạo ảnh", 28f, lines = 2) to 28,
            layout("THÔNG TIN SẢN PHẨM", 26f, true, 1) to 16,
            layout(product.description.ifBlank { "Chưa có mô tả." }, 32f) to 40,
            layout("Giá tham khảo · Nội dung tóm tắt\nBloomy Beauty — Ứng dụng học tập\nẢnh tạo lúc ${date.format(Instant.ofEpochMilli(createdAt))} (giờ Việt Nam)", 24f, lines = 3, color = Color.rgb(105, 96, 102)) to 0,
        )
        val height = 800 + blocks.sumOf { it.first.height + it.second } + 64
        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(255, 247, 250))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), 12f, paint)
            paint.textSize = 52f; paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("Bloomy Beauty", 64f, 92f, paint)
            paint.textSize = 24f; paint.typeface = Typeface.DEFAULT
            canvas.drawText("CHIA SẺ SẢN PHẨM", 64f, 134f, paint)
            paint.color = Color.WHITE
            canvas.drawRoundRect(RectF(64f, 176f, 1016f, 736f), 28f, 28f, paint)
            val resource = productImage(product.imageKey)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
            BitmapFactory.decodeResource(context.resources, resource, options)
            options.inSampleSize = 1
            while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 900) options.inSampleSize *= 2
            options.inJustDecodeBounds = false
            val photo = BitmapFactory.decodeResource(context.resources, resource, options)
            if (photo != null) {
                try {
                    val scale = minOf(904f / photo.width, 512f / photo.height)
                    val w = photo.width * scale; val h = photo.height * scale
                    canvas.drawBitmap(photo, null, RectF(540f - w / 2, 456f - h / 2, 540f + w / 2, 456f + h / 2), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                } finally { photo.recycle() }
            } else {
                paint.color = accent; paint.textSize = 36f
                canvas.drawText("Bloomy Beauty", 390f, 460f, paint)
            }
            var y = 800f
            blocks.forEachIndexed { index, (text, gap) ->
                if (index == blocks.lastIndex) {
                    paint.color = Color.rgb(225, 209, 218); paint.strokeWidth = 2f
                    canvas.drawLine(64f, y - 20f, 1016f, y - 20f, paint)
                }
                canvas.save(); canvas.translate(64f, y); text.draw(canvas); canvas.restore()
                y += text.height + gap
            }
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("Cannot encode product image")
        } finally { bitmap.recycle() }
    }
}
