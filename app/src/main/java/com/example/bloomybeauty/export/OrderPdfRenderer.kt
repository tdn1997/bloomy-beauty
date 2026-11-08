package com.example.bloomybeauty.export

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.bloomybeauty.data.order.CustomerOrder
import com.example.bloomybeauty.feature.order.orderStatusLabel
import java.io.OutputStream
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Draws persisted order snapshots only. StaticLayout wraps Vietnamese, including very long fields. */
internal object OrderPdfRenderer {
    fun write(order: CustomerOrder, output: OutputStream) {
        val money = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))
        fun price(value: Long) = "${money.format(value)} đ"
        val date = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
        val document = PdfDocument()
        var page: PdfDocument.Page? = null
        try {
            var number = 0
            var y = 0f
            val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color = Color.BLACK; textSize = 11f}
            fun finish() {page?.let {document.finishPage(it)};page = null}
            fun next() {
                finish();number++
                page = document.startPage(PdfDocument.PageInfo.Builder(595,842,number).create())
                paint.textSize = 9f;paint.typeface = Typeface.DEFAULT
                page!!.canvas.drawText("Bloomy Beauty • ${order.code}",36f,26f,paint)
                page!!.canvas.drawText("Trang $number • Bản demo",420f,820f,paint)
                y = 48f
            }
            fun text(value: String, bold: Boolean = false, size: Float = 11f) {
                paint.textSize = size;paint.typeface = if(bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                val layout = StaticLayout.Builder.obtain(value,0,value.length,paint,523)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(3f,1f).build()
                // Draw each visual line separately, so even one address longer than a page remains complete.
                for(i in 0 until layout.lineCount) {
                    val top = layout.getLineTop(i);val bottom = layout.getLineBottom(i)
                    val height = (bottom-top).toFloat()
                    if(page == null || y+height > 794f) next()
                    // next() draws headers using the same paint: restore body style before the layout.
                    paint.textSize = size;paint.typeface = if(bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                    val canvas = page!!.canvas
                    canvas.save();canvas.clipRect(36f,y,559f,y+height)
                    canvas.translate(36f,y-top);layout.draw(canvas);canvas.restore()
                    y += height
                }
                y += 7f
            }
            next()
            text("Phiếu đơn hàng — bản demo",true,18f)
            text("Mã đơn: ${order.code}",true)
            text("Ngày đặt: ${date.format(Instant.ofEpochMilli(order.createdAt))} (giờ Việt Nam)")
            text("Trạng thái: ${orderStatusLabel(order.status)}",true)
            text("Thanh toán khi nhận hàng (COD)")
            if(order.status == "CANCELLED") text("ĐƠN ĐÃ HỦY — không yêu cầu thanh toán.",true)
            text("NGƯỜI NHẬN",true)
            text(order.shipping.recipient);text("Điện thoại: ${order.shipping.phone}");text("Địa chỉ: ${order.shipping.address}")
            text("SẢN PHẨM THEO ĐƠN ĐÃ LƯU",true)
            order.lines.forEachIndexed {index,line ->
                text("${index+1}. ${line.name} • ${line.volume}",true)
                text("Số lượng: ${line.quantity} • Đơn giá: ${price(line.unitPriceVnd)} • Thành tiền: ${price(line.subtotalVnd)}")
            }
            text("Tạm tính: ${price(order.subtotalVnd)}")
            text("Mã giảm giá demo: ${order.couponCode ?: "Không áp dụng"}")
            text("Giảm giá: ${price(order.discountVnd)}")
            text("Phí giao hàng: ${price(order.feeVnd)}")
            text("Tổng COD: ${price(order.totalVnd)}",true,14f)
            text("Phiếu minh họa của ứng dụng học tập, không phải hóa đơn thuế.",size=9f)
            finish()
            // Android native PdfDocument may swallow an IOException raised by a Java stream.
            // Render to memory, then let the Java stream write propagate a real disk failure.
            val bytes = java.io.ByteArrayOutputStream()
            document.writeTo(bytes)
            bytes.writeTo(output)
        } finally {
            page?.let { document.finishPage(it) }
            document.close()
        }
    }
}
