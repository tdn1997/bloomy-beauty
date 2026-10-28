package com.example.bloomybeauty.data.promotions

/** Public advertising data. Neither model authorizes a checkout discount or reserves stock. */
data class FlashSale(
    val id: String, val title: String, val productId: String, val salePriceVnd: Long,
    val startsAt: Long, val endsAt: Long,
) {
    fun phase(now: Long): PromotionPhase = phaseAt(startsAt, endsAt, now)
}

data class CouponCampaign(
    val id: String, val title: String, val code: String, val discountType: DiscountType,
    val discountValue: Long, val minOrderVnd: Long, val maxDiscountVnd: Long?,
    val startsAt: Long, val endsAt: Long,
) {
    fun phase(now: Long): PromotionPhase = phaseAt(startsAt, endsAt, now)
}

enum class DiscountType { FIXED, PERCENT }
enum class PromotionPhase { UPCOMING, ACTIVE, EXPIRED }
private fun phaseAt(start: Long, end: Long, now: Long): PromotionPhase = when {
    now < start -> PromotionPhase.UPCOMING
    now < end -> PromotionPhase.ACTIVE
    else -> PromotionPhase.EXPIRED
}

enum class PromotionsError { PERMISSION_DENIED, UNAVAILABLE }
data class PromotionsFeed(
    val enabled: Boolean = true,
    val loading: Boolean = true,
    val flashSales: List<FlashSale> = emptyList(),
    val coupons: List<CouponCampaign> = emptyList(),
    val fromCache: Boolean = false,
    val error: PromotionsError? = null,
)

/** Strict schema decoder; timestamp fields have been converted to epoch milliseconds by the source. */
object PromotionDecoder {
    // Limit money fields to safe, useful VND amounts and reject floating-point prices.
    private const val MAX_MONEY = 1_000_000_000L
    private val codePattern = Regex("[A-Z0-9_-]{3,32}")

    fun flashSale(id: String, fields: Map<String, Any?>): FlashSale? {
        if (fields["published"] != true) return null
        val title = text(fields, "title", 120) ?: return null
        val productId = text(fields, "productId", 80) ?: return null
        val price = money(fields, "salePriceVnd")?.takeIf { it > 0 } ?: return null
        val (start, end) = window(fields) ?: return null
        return FlashSale(id, title, productId, price, start, end)
    }

    fun coupon(id: String, fields: Map<String, Any?>): CouponCampaign? {
        if (fields["published"] != true) return null
        val title = text(fields, "title", 120) ?: return null
        val code = (fields["code"] as? String)?.takeIf { codePattern.matches(it) } ?: return null
        val type = when (fields["discountType"]) {
            "fixed" -> DiscountType.FIXED
            "percent" -> DiscountType.PERCENT
            else -> return null
        }
        val value = money(fields, "discountValue")?.takeIf { it > 0 } ?: return null
        if (type == DiscountType.PERCENT && value > 100) return null
        val minimum = money(fields, "minOrderVnd") ?: return null
        val cap = if (fields["maxDiscountVnd"] == null) null else
            money(fields, "maxDiscountVnd")?.takeIf { it > 0 } ?: return null
        if (type == DiscountType.PERCENT && cap == null) return null
        val (start, end) = window(fields) ?: return null
        return CouponCampaign(id, title, code, type, value, minimum, cap, start, end)
    }

    private fun money(fields: Map<String, Any?>, key: String): Long? =
        (fields[key] as? Long)?.takeIf { it in 0..MAX_MONEY }

    private fun text(fields: Map<String, Any?>, key: String, max: Int): String? =
        (fields[key] as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= max }

    private fun window(fields: Map<String, Any?>): Pair<Long, Long>? {
        val start = fields["startsAt"] as? Long ?: return null
        val end = fields["endsAt"] as? Long ?: return null
        return if (start >= 0 && end > start) start to end else null
    }
}
