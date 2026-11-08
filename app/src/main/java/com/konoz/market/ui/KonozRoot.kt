package com.konoz.market.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.konoz.market.R
import com.konoz.market.ui.products.ProductsRoute

private data class Tab(val labelRes: Int, val icon: ImageVector)

/** Cashier is first, per the product requirement: the cashier must be at the front of the app. */
private val tabs = listOf(
    Tab(R.string.nav_cashier, Icons.Filled.PointOfSale),
    Tab(R.string.nav_dashboard, Icons.Filled.Dashboard),
    Tab(R.string.nav_products, Icons.Filled.Inventory2),
    Tab(R.string.nav_more, Icons.Filled.Menu),
)

private const val TAB_PRODUCTS = 2

@Composable
fun KonozRoot() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        when (selected) {
            TAB_PRODUCTS -> ProductsRoute(Modifier.padding(padding))
            else -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(tabs[selected].labelRes) + "\n" + stringResource(R.string.placeholder_soon),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
