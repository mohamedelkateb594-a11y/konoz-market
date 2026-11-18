package com.konoz.market.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a slim scroll bar (a track and a thumb) along the end edge of a LazyColumn: right in English, left in Arabic.
 * It shows where the visible items sit in the whole list and is hidden when everything fits on screen.
 * Item sizes are estimated from the visible items, so the thumb size is approximate for lists of mixed row heights.
 * It is an indicator only (not draggable). Colors come from the theme.
 */
@Composable
fun Modifier.verticalScrollbar(
    state: LazyListState,
    thickness: Dp = 6.dp,
    thumbColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
    trackColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (visible.isEmpty() || info.totalItemsCount == 0) return@drawWithContent

        val viewport = size.height
        val first = visible.first()
        val last = visible.last()
        // Average row height including the spacing between rows.
        val average = (last.offset + last.size - first.offset).toFloat() / visible.size
        val padding = (info.beforeContentPadding + info.afterContentPadding).toFloat()
        val total = average * info.totalItemsCount + padding
        if (total <= viewport + 1f) return@drawWithContent

        val scrolled = first.index * average + (info.beforeContentPadding - first.offset)
        val fraction = (scrolled / (total - viewport)).coerceIn(0f, 1f)
        val minThumb = 28.dp.toPx()
        val thumbHeight = min(viewport, max(minThumb, viewport * viewport / total))
        val top = fraction * (viewport - thumbHeight)

        val thick = thickness.toPx()
        val x = if (rtl) 0f else size.width - thick
        val radius = CornerRadius(thick / 2f, thick / 2f)
        drawRoundRect(trackColor, Offset(x, 0f), Size(thick, viewport), radius)
        drawRoundRect(thumbColor, Offset(x, top), Size(thick, thumbHeight), radius)
    }
}
