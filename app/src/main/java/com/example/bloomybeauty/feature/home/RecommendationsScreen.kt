package com.example.bloomybeauty.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.bloomybeauty.R
import com.example.bloomybeauty.data.catalog.ProductRecommendation
import com.example.bloomybeauty.data.catalog.ProductRecommendations
import com.example.bloomybeauty.data.catalog.RecommendationReason

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecommendationsScreen(
    state: HomeUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recommendations_for_you)) },
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back_action))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                state.error -> {
                    Text(stringResource(R.string.catalog_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onRetry) { Text(stringResource(R.string.retry_action)) }
                }
                else -> {
                    val recommendations = remember(state.products, state.favoriteIds) {
                        ProductRecommendations.forHome(state.products, state.favoriteIds)
                    }
                    if (recommendations.isEmpty()) {
                        Text(stringResource(R.string.recommendations_empty))
                    } else {
                        if (state.favoriteIds.isEmpty()) {
                            Text(
                                stringResource(R.string.recommendations_favorite_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RecommendationList(recommendations, onOpen)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecommendationList(
    recommendations: List<ProductRecommendation>,
    onOpen: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        recommendations.forEach { recommendation ->
            val product = recommendation.product
            val reason = when (recommendation.reason) {
                RecommendationReason.DISCOVER -> null
                RecommendationReason.FAVORITE -> stringResource(R.string.recommendation_favorite, recommendation.favoriteName)
                RecommendationReason.SAME_CATEGORY -> stringResource(R.string.recommendation_same_category)
                RecommendationReason.SIMILAR_CONTENT -> stringResource(R.string.recommendation_similar_content)
            }
            Surface(
                onClick = { onOpen(product.id) },
                modifier = Modifier.fillMaxWidth().testTag("recommendation-${product.id}"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painterResource(productImage(product.imageKey)),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color.White),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(product.name, style = MaterialTheme.typography.titleSmall)
                        Text(formatPrice(product.priceVnd), style = MaterialTheme.typography.labelLarge)
                        if (reason != null) {
                            Text(
                                reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
