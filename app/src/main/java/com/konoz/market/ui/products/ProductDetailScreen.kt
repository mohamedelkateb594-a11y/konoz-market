package com.konoz.market.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.ExpiryDates
import com.konoz.market.core.Money
import com.konoz.market.data.entity.Product
import com.konoz.market.ui.common.KonozIconBadge
import com.konoz.market.ui.common.ProductImage
import com.konoz.market.ui.common.formatQty
import com.konoz.market.ui.common.unitLabelRes
import com.konoz.market.ui.theme.CardKind
import com.konoz.market.ui.theme.KonozTheme_
import com.konoz.market.ui.theme.cardBrush
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** The property screens that open from the product details screen. Saved as a string by the route. */
enum class ProductDetailSection { PRICING, STOCK, BARCODES }

// ---------------------------------------------------------------------------------------------------------------------
// Details screen
// ---------------------------------------------------------------------------------------------------------------------

/**
 * Product details: a header card, then one card per property group. Pricing, stock and barcodes open their own screen
 * ([onOpenSection]); category, supplier, code and expiry are shown as plain cards. Editing is [onEdit].
 */
@Composable
fun ProductDetailScreen(
    vm: ProductDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenSection: (ProductDetailSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val p = ui.product
    Column(modifier.fillMaxSize()) {
        DetailTopBar(
            title = p?.name ?: "",
            onBack = onBack,
            actionIcon = if (p != null) Icons.Rounded.Edit else null,
            actionDescription = stringResource(R.string.pd_edit),
            onAction = onEdit,
        )
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            p == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.pd_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { HeaderCard(p) }
                item {
                    PropertyCard(
                        kind = CardKind.MONEY,
                        icon = Icons.Rounded.Sell,
                        label = stringResource(R.string.pd_pricing),
                        value = Money.format(p.salePrice),
                        onClick = { onOpenSection(ProductDetailSection.PRICING) },
                    )
                }
                item {
                    val low = isLow(p)
                    PropertyCard(
                        kind = if (low) CardKind.ALERT else CardKind.STOCK,
                        icon = Icons.Rounded.Inventory2,
                        label = stringResource(R.string.pd_stock),
                        value = stringResource(R.string.stock_fmt, formatQty(p.currentQtyMilli), stringResource(unitLabelRes(p.unit))),
                        valueColor = if (low) MaterialTheme.colorScheme.onErrorContainer else Color.Unspecified,
                        onClick = { onOpenSection(ProductDetailSection.STOCK) },
                    )
                }
                item {
                    PropertyCard(
                        kind = CardKind.INFO,
                        icon = Icons.Rounded.QrCode2,
                        label = stringResource(R.string.pd_barcodes),
                        value = stringResource(R.string.pd_barcode_count_fmt, ui.barcodes.size),
                        onClick = { onOpenSection(ProductDetailSection.BARCODES) },
                    )
                }
                item {
                    PropertyCard(
                        kind = CardKind.INFO,
                        icon = Icons.Rounded.Category,
                        label = stringResource(R.string.pd_category),
                        value = ui.categoryName ?: stringResource(R.string.pd_none),
                    )
                }
                item {
                    PropertyCard(
                        kind = CardKind.INFO,
                        icon = Icons.Rounded.LocalShipping,
                        label = stringResource(R.string.pd_supplier),
                        value = ui.supplierName ?: stringResource(R.string.pd_none),
                    )
                }
                item {
                    PropertyCard(
                        kind = CardKind.NEUTRAL,
                        icon = Icons.Rounded.Tag,
                        label = stringResource(R.string.pd_code),
                        value = p.internalCode?.takeIf { it.isNotBlank() } ?: stringResource(R.string.pd_none),
                    )
                }
                item {
                    val expired = p.expiryDate?.let { it < System.currentTimeMillis() } == true
                    PropertyCard(
                        kind = if (expired) CardKind.ALERT else CardKind.NEUTRAL,
                        icon = Icons.Rounded.Event,
                        label = stringResource(R.string.pd_expiry),
                        value = p.expiryDate?.let { formatExpiry(it) } ?: stringResource(R.string.pd_none),
                        valueColor = if (expired) MaterialTheme.colorScheme.onErrorContainer else Color.Unspecified,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Property screens (pricing, stock, barcodes)
// ---------------------------------------------------------------------------------------------------------------------

/** One property group on its own screen. Read only; the back arrow returns to the details screen. */
@Composable
fun ProductSectionScreen(
    vm: ProductDetailViewModel,
    section: ProductDetailSection,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val p = ui.product
    val title = when (section) {
        ProductDetailSection.PRICING -> R.string.pd_pricing
        ProductDetailSection.STOCK -> R.string.pd_stock
        ProductDetailSection.BARCODES -> R.string.pd_barcodes
    }
    Column(modifier.fillMaxSize()) {
        DetailTopBar(title = stringResource(title), onBack = onBack)
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            p == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.pd_not_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (section) {
                    ProductDetailSection.PRICING -> pricingItems(p)
                    ProductDetailSection.STOCK -> stockItems(p)
                    ProductDetailSection.BARCODES -> {
                        if (ui.barcodes.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.pd_no_barcodes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        } else {
                            items(ui.barcodes, key = { it.code }) { b ->
                                PropertyCard(
                                    kind = CardKind.INFO,
                                    icon = Icons.Rounded.QrCode2,
                                    label = b.code,
                                    value = stringResource(R.string.pd_barcode_units_fmt, b.unitsPerScan),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.pricingItems(p: Product) {
    val profit = p.salePrice - p.costPrice
    item { PropertyCard(CardKind.MONEY, Icons.Rounded.Payments, stringResource(R.string.pd_sale_price), Money.format(p.salePrice)) }
    item { PropertyCard(CardKind.NEUTRAL, Icons.Rounded.Payments, stringResource(R.string.pd_cost), Money.format(p.costPrice)) }
    item {
        PropertyCard(
            CardKind.MONEY, Icons.Rounded.Sell, stringResource(R.string.pd_profit), Money.format(profit),
            valueColor = if (profit < 0) MaterialTheme.colorScheme.onErrorContainer else Color.Unspecified,
        )
    }
    if (p.salePrice > 0) {
        // Whole basis points of the sale price; display only, so plain Long math (no floating point).
        val bp = profit * 10_000 / p.salePrice
        val text = (if (bp < 0) "-" else "") + (Math.abs(bp) / 100) + "." + (Math.abs(bp) % 100).toString().padStart(2, '0') + "%"
        item { PropertyCard(CardKind.INFO, Icons.Rounded.Sell, stringResource(R.string.pd_margin), text) }
    }
}

private fun LazyListScope.stockItems(p: Product) {
    val low = isLow(p)
    item {
        val unit = stringResource(unitLabelRes(p.unit))
        PropertyCard(
            if (low) CardKind.ALERT else CardKind.STOCK, Icons.Rounded.Inventory2, stringResource(R.string.pd_current_qty),
            stringResource(R.string.stock_fmt, formatQty(p.currentQtyMilli), unit),
            valueColor = if (low) MaterialTheme.colorScheme.onErrorContainer else Color.Unspecified,
        )
    }
    item {
        val unit = stringResource(unitLabelRes(p.unit))
        PropertyCard(
            CardKind.NEUTRAL, Icons.Rounded.Inventory2, stringResource(R.string.pd_low_threshold),
            stringResource(R.string.stock_fmt, formatQty(p.lowStockThresholdMilli), unit),
        )
    }
    val perCarton = p.piecesPerCarton
    if (perCarton != null && perCarton > 0 && p.currentQtyMilli >= 0) {
        val perCartonMilli = perCarton * 1000L
        val cartons = p.currentQtyMilli / perCartonMilli
        val rest = p.currentQtyMilli % perCartonMilli
        item {
            PropertyCard(
                CardKind.INFO, Icons.Rounded.Inventory2, stringResource(R.string.pd_cartons),
                stringResource(R.string.pd_cartons_fmt, cartons.toString(), formatQty(rest), perCarton.toString()),
            )
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (low) StatusPill(stringResource(R.string.low_stock), KonozTheme_.colors.warningContainer, KonozTheme_.colors.onWarningContainer)
            else StatusPill(stringResource(R.string.pd_stock_ok), KonozTheme_.colors.successContainer, KonozTheme_.colors.onSuccessContainer)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------------------------------

private fun isLow(p: Product) = p.currentQtyMilli <= p.lowStockThresholdMilli

private fun formatExpiry(stored: Long): String {
    // ExpiryDates gives UTC-midnight millis of the calendar day, so format in UTC to keep the day the user picked.
    val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    f.timeZone = TimeZone.getTimeZone("UTC")
    return f.format(java.util.Date(ExpiryDates.toPickerMillis(stored)))
}

@Composable
private fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actionIcon: ImageVector? = null,
    actionDescription: String = "",
    onAction: () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.pd_back))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionIcon != null) {
            IconButton(onClick = onAction) { Icon(actionIcon, contentDescription = actionDescription, tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun HeaderCard(p: Product) {
    GradientCard(CardKind.MONEY, onClick = null) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProductImage(p.imagePath, 96.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(Money.format(p.salePrice), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (p.isActive) StatusPill(stringResource(R.string.pd_active), KonozTheme_.colors.successContainer, KonozTheme_.colors.onSuccessContainer)
                    else StatusPill(stringResource(R.string.inactive), MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
                    if (p.isFavorite) {
                        Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.favorite_label), tint = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

/** A card with a soft mint/cream gradient by [kind]. Tappable when [onClick] is given. */
@Composable
private fun GradientCard(kind: CardKind, onClick: (() -> Unit)?, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    val elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    val inner: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().background(cardBrush(kind))) { content() }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = elevation) { inner() }
    } else {
        Card(modifier = Modifier.fillMaxWidth(), shape = shape, colors = colors, elevation = elevation) { inner() }
    }
}

/**
 * Icon badge + label + value. A chevron shows when the card opens a screen.
 * The label is onSurface at 85% (about 6:1 on the darkest mint), because onSurfaceVariant is only 3.5:1 on mint.
 */
@Composable
private fun PropertyCard(
    kind: CardKind,
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
) {
    val soft = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    GradientCard(kind, onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            KonozIconBadge(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = soft)
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
                )
            }
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = soft)
            }
        }
    }
}
