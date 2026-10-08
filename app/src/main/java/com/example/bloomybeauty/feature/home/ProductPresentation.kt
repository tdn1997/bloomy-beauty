package com.example.bloomybeauty.feature.home

import androidx.annotation.DrawableRes
import com.example.bloomybeauty.R
import java.text.NumberFormat
import java.util.Locale

fun formatPrice(priceVnd: Long): String = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))
    .format(priceVnd) + " ₫"

@DrawableRes
fun productImage(key: String): Int = when (key) {
    "product_cleanser" -> R.drawable.product_cleanser
    "product_micellar" -> R.drawable.product_micellar
    "product_serum" -> R.drawable.product_serum
    "product_toner" -> R.drawable.product_toner
    "product_body_scrub" -> R.drawable.product_body_scrub
    "product_shampoo" -> R.drawable.product_shampoo
    "product_lip_scrub" -> R.drawable.product_lip_scrub
    "product_lip_balm" -> R.drawable.product_lip_balm
    else -> R.drawable.ic_launcher_foreground
}
