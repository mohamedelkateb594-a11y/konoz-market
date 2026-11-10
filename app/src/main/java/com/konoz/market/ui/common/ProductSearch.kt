package com.konoz.market.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.konoz.market.data.entity.Product

/** Search box with a short result list underneath (stateless: the caller owns the query and the results). */
@Composable
fun ProductSearch(
    query: String,
    results: List<Product>,
    label: String,
    onQuery: (String) -> Unit,
    onPick: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text(label) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (query.isNotBlank()) {
            results.take(8).forEach { p ->
                Card(onClick = { onPick(p) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(p.name, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}
