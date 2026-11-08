package com.example.bloomybeauty.data.coupon

import java.math.BigInteger
import java.util.Locale

enum class CouponKind { FIXED, PERCENT }
enum class CouponError { DISABLED, NOT_FOUND, INACTIVE, NOT_STARTED, EXPIRED, MINIMUM, INVALID_RULE }
class CouponException(val error: CouponError, val minimumSubtotal: Long? = null) : Exception(error.name)
fun normalizeCoupon(code: String): String = code.trim().uppercase(Locale.ROOT)

data class CouponRule(val code: String, val kind: CouponKind, val value: Long, val minimumSubtotal: Long,
    val maximumDiscount: Long?, val startsAt: Long, val endsAt: Long, val active: Boolean, val revision: Long) {
    fun quote(subtotal: Long, now: Long): CouponQuote {
        require(subtotal >= 0)
        if (!code.matches(Regex("[A-Z0-9_-]{3,32}")) || value <= 0 || minimumSubtotal < 0 ||
            (maximumDiscount != null && maximumDiscount < 0) || endsAt <= startsAt || revision < 0 ||
            (kind == CouponKind.PERCENT && value > 100)) throw CouponException(CouponError.INVALID_RULE)
        if (!active) throw CouponException(CouponError.INACTIVE)
        if (now < startsAt) throw CouponException(CouponError.NOT_STARTED)
        if (now >= endsAt) throw CouponException(CouponError.EXPIRED)
        if (subtotal < minimumSubtotal) throw CouponException(CouponError.MINIMUM,minimumSubtotal)
        val raw = when(kind) {
            CouponKind.FIXED -> value
            CouponKind.PERCENT -> {
                val amount = BigInteger.valueOf(subtotal).multiply(BigInteger.valueOf(value)).divide(BigInteger.valueOf(100))
                if(amount.signum() < 0 || amount.bitLength() > 63) throw ArithmeticException("Coupon discount overflow")
                amount.toLong() // longValueExact is only available from Android API 31.
            }
        }
        return CouponQuote(this,minOf(subtotal,raw,maximumDiscount ?: Long.MAX_VALUE))
    }
    internal fun identity(): String = listOf(code,kind.name,value.toString(),minimumSubtotal.toString(),
        maximumDiscount?.toString() ?: "none",startsAt.toString(),endsAt.toString(),active.toString(),revision.toString())
        .joinToString("") {"${it.length}:$it"}
}
data class CouponQuote(val rule: CouponRule, val discountVnd: Long)
