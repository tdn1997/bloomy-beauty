package com.example.bloomybeauty.data.promotions

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

interface PromotionsRepository {
    fun observe(): Flow<PromotionsFeed>
}

object DisabledPromotionsRepository : PromotionsRepository {
    override fun observe(): Flow<PromotionsFeed> = flowOf(PromotionsFeed(enabled = false, loading = false))
}

class FirestorePromotionsRepository(private val firestore: FirebaseFirestore) : PromotionsRepository {
    override fun observe(): Flow<PromotionsFeed> = combine(
        listen(firestore.collection(FLASH_SALES).whereEqualTo("published", true), PromotionDecoder::flashSale),
        listen(firestore.collection(COUPON_CAMPAIGNS).whereEqualTo("published", true), PromotionDecoder::coupon),
    ) { sales, coupons ->
        PromotionsFeed(
            loading = sales.loading || coupons.loading,
            flashSales = sales.entries.sortedWith(compareBy<FlashSale> { it.startsAt }.thenBy { it.id }),
            coupons = coupons.entries.sortedWith(compareBy<CouponCampaign> { it.endsAt }.thenBy { it.id }),
            fromCache = sales.fromCache || coupons.fromCache,
            error = listOfNotNull(sales.error, coupons.error).firstOrNull(),
        )
    }

    private data class Page<T>(
        val entries: List<T> = emptyList(), val loading: Boolean = true,
        val fromCache: Boolean = false, val error: PromotionsError? = null,
    )

    private fun <T> listen(query: Query, decode: (String, Map<String, Any?>) -> T?): Flow<Page<T>> = callbackFlow {
        trySend(Page())
        val registration = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                // A terminal listener error discards that collection's previous offers.
                trySend(Page(loading = false, error = if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                    PromotionsError.PERMISSION_DENIED else PromotionsError.UNAVAILABLE))
            } else if (snapshot != null) {
                val entries = snapshot.documents.mapNotNull { document ->
                    val fields = mutableMapOf<String, Any?>().apply { putAll(document.data.orEmpty()) }
                    // Only Firestore Timestamp is accepted on the wire, never a date string or number.
                    for (key in listOf("startsAt", "endsAt")) {
                        fields[key] = (fields[key] as? Timestamp)?.toDate()?.time
                    }
                    decode(document.id, fields)
                }
                trySend(Page(entries, loading = false, fromCache = snapshot.metadata.isFromCache))
            }
        }
        awaitClose { registration.remove() }
    }

    companion object {
        const val FLASH_SALES = "flash_sales"
        const val COUPON_CAMPAIGNS = "coupon_campaigns"
    }
}
