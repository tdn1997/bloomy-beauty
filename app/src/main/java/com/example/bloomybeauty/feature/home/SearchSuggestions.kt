package com.example.bloomybeauty.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.bloomybeauty.R
import com.example.bloomybeauty.data.catalog.Product
import com.example.bloomybeauty.data.catalog.SearchAiRanker
import com.example.bloomybeauty.data.catalog.SearchCandidate
import com.example.bloomybeauty.data.catalog.SearchReason
import com.example.bloomybeauty.data.catalog.SearchRecommendations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun SearchSuggestions(
    query: String,
    matches: List<Product>,
    catalog: List<Product>,
    categoryId: String?,
    ranker: SearchAiRanker?,
    onOpen: (String) -> Unit,
) {
    val goal = remember(query) { SearchRecommendations.goal(query) }
    val products = remember(catalog, categoryId) {
        catalog.filter { it.active && it.stock > 0 && (categoryId == null || categoryId == it.categoryId) }
            .associateBy { it.id }
    }
    val candidates = remember(goal, products) {
        goal?.let { SearchRecommendations.candidates(it, products.values.toList()) }.orEmpty()
    }
    val local = (matches.filter { it.id in products }.map { it.id } + candidates.map { it.productId })
        .distinct().take(3).mapNotNull(products::get)
    val canAskAi = ranker != null && goal != null && candidates.isNotEmpty()
    if (!canAskAi && local.isEmpty()) return

    key(query, products, ranker) {
        val scope = rememberCoroutineScope()
        var ranked by remember { mutableStateOf(emptyList<SearchCandidate>()) }
        var pending by remember { mutableStateOf(false) }
        var fallback by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("search-suggestions"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                if (canAskAi && ranked.isEmpty()) {
                    TextButton(
                        enabled = !pending,
                        onClick = {
                            if (pending) return@TextButton
                            pending = true
                            fallback = false
                            scope.launch {
                                try {
                                    val response = withTimeoutOrNull(5_000) { ranker.rank(goal, candidates) }
                                    ranked = SearchRecommendations.validate(response.orEmpty(), candidates)
                                    fallback = ranked.isEmpty()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    fallback = true
                                } finally {
                                    pending = false
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.search_ask_ai)) }
                    Text(
                        stringResource(if (fallback) R.string.search_ai_fallback else R.string.search_ai_data_notice),
                        Modifier.padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (pending) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                if (ranked.isEmpty()) {
                    local.forEach { SearchSuggestionRow(it, null, onOpen) }
                } else {
                    Text(
                        stringResource(R.string.search_ai_results),
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    ranked.forEach { candidate ->
                        products[candidate.productId]?.let { SearchSuggestionRow(it, candidate.reason, onOpen) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSuggestionRow(product: Product, reason: SearchReason?, onOpen: (String) -> Unit) {
    Surface(
        onClick = { onOpen(product.id) },
        modifier = Modifier.fillMaxWidth().testTag("search-suggestion-${product.id}"),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painterResource(productImage(product.imageKey)), null,
                Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(Color.White),
            )
            Column(Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (reason != null) {
                    val label = when (reason) {
                        SearchReason.FACE_WASH -> R.string.search_reason_face_wash
                        SearchReason.MAKEUP_REMOVER -> R.string.search_reason_makeup_remover
                        SearchReason.LIP_BALM -> R.string.search_reason_lip_balm
                        SearchReason.LIP_EXFOLIANT -> R.string.search_reason_lip_exfoliant
                        SearchReason.SHAMPOO -> R.string.search_reason_shampoo
                    }
                    Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(formatPrice(product.priceVnd), style = MaterialTheme.typography.labelMedium)
        }
    }
}
