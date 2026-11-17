package com.konoz.market.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.konoz.market.R
import com.konoz.market.ui.reports.ReportSections
import com.konoz.market.ui.theme.ReportCardTokens
import kotlin.math.abs

/** One report card: the Reports screen section it opens ([sectionId]), its name, short description and icon. */
private class ReportCardSpec(val sectionId: String, val titleRes: Int, val descRes: Int, val icon: ImageVector)

/** Same nine reports, in the same order, as the sections of the Reports screen (ReportSections.order). */
private val reportCards = listOf(
    ReportCardSpec(ReportSections.SUMMARY, R.string.rcard_summary_title, R.string.rcard_summary_desc, Icons.Filled.Assessment),
    ReportCardSpec(ReportSections.TOP_QTY, R.string.rcard_top_qty_title, R.string.rcard_top_qty_desc, Icons.Filled.Whatshot),
    ReportCardSpec(ReportSections.TOP_PROFIT, R.string.rcard_top_profit_title, R.string.rcard_top_profit_desc, Icons.Filled.Savings),
    ReportCardSpec(ReportSections.LEAST_SOLD, R.string.rcard_least_sold_title, R.string.rcard_least_sold_desc, Icons.Filled.ArrowDownward),
    ReportCardSpec(ReportSections.NON_MOVING, R.string.rcard_non_moving_title, R.string.rcard_non_moving_desc, Icons.Filled.HourglassEmpty),
    ReportCardSpec(ReportSections.CATEGORIES, R.string.rcard_categories_title, R.string.rcard_categories_desc, Icons.Filled.Category),
    ReportCardSpec(ReportSections.EXPENSES, R.string.rcard_expenses_title, R.string.rcard_expenses_desc, Icons.Filled.Payments),
    ReportCardSpec(ReportSections.PEAK_HOURS, R.string.rcard_peak_title, R.string.rcard_peak_desc, Icons.Filled.Schedule),
    ReportCardSpec(ReportSections.WEEKDAYS, R.string.rcard_weekdays_title, R.string.rcard_weekdays_desc, Icons.Filled.CalendarMonth),
)

/**
 * Dashboard title with the prominent start-cashier button at the end of the row
 * (the top-left corner in Arabic; the layout mirrors automatically).
 */
@Composable
internal fun DashboardHeader(onStartCashier: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.dash_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Button(
            onClick = onStartCashier,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        ) {
            Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.dash_start_cashier), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The reports as a horizontal strip of landscape cards. Swipe left or right; the strip snaps to a card, and the cards
 * near the edges shrink and fade a little, so moving between them is smooth.
 * Tapping a card calls [onOpenReport] with the id of its section, and the Reports screen opens scrolled to it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReportCardsStrip(onOpenReport: (String) -> Unit, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = listState)
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val cardWidth = (screenWidthDp * 0.78f).toInt().coerceIn(240, 300).dp

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.rcard_section), style = MaterialTheme.typography.titleMedium)
        LazyRow(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(reportCards, key = { _, spec -> spec.sectionId }) { index, spec ->
                ReportCard(
                    spec = spec,
                    style = ReportCardTokens.styles[index % ReportCardTokens.styles.size],
                    onClick = { onOpenReport(spec.sectionId) },
                    modifier = Modifier
                        .width(cardWidth)
                        .height(116.dp)
                        .graphicsLayer {
                            val info = listState.layoutInfo
                            val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                            val far = if (item == null) {
                                1f
                            } else {
                                val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
                                val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                                val itemCenter = item.offset + item.size / 2f
                                (abs(itemCenter - viewportCenter) / viewport).coerceIn(0f, 1f)
                            }
                            val scale = 1f - 0.06f * far
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - 0.15f * far
                        },
                )
            }
        }
    }
}

@Composable
private fun ReportCard(
    spec: ReportCardSpec,
    style: ReportCardTokens.Style,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = style.gradientEnd),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(style.gradientStart, style.gradientEnd)))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(52.dp).background(ReportCardTokens.IconBadge, CircleShape), contentAlignment = Alignment.Center) {
                Icon(spec.icon, contentDescription = null, tint = style.icon, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(spec.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ReportCardTokens.Title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(spec.descRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReportCardTokens.Description,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
