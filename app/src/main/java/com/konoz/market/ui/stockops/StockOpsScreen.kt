package com.konoz.market.ui.stockops

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.data.entity.Product
import com.konoz.market.ui.common.ProductSearch
import com.konoz.market.ui.common.formatQty
import com.konoz.market.ui.common.unitLabelRes

@Composable
fun StockOpsScreen(vm: StockOpsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
            Text(stringResource(R.string.stock_ops_title), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ops = listOf(
                    StockOp.ADJUST to R.string.op_adjust, StockOp.DAMAGED to R.string.op_damaged,
                    StockOp.EXPIRED to R.string.op_expired, StockOp.CONVERT to R.string.op_convert,
                )
                items(ops.size) { i ->
                    FilterChip(selected = state.op == ops[i].first, onClick = { vm.setOp(ops[i].first) }, label = { Text(stringResource(ops[i].second)) })
                }
            }

            if (state.product == null) {
                ProductSearch(query, results, stringResource(R.string.pick_product), vm::setQuery, vm::pick)
            } else {
                ProductCard(state.product!!, onChange = vm::clearProduct)
            }

            if (state.op == StockOp.ADJUST) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.increase, onClick = { vm.setIncrease(true) }, label = { Text(stringResource(R.string.op_increase)) })
                    FilterChip(selected = !state.increase, onClick = { vm.setIncrease(false) }, label = { Text(stringResource(R.string.op_decrease)) })
                }
            }

            NumberField(stringResource(R.string.ops_qty), state.qty, vm::setQty)

            if (state.op == StockOp.CONVERT) {
                if (state.target == null) {
                    // the search box is shared: only show it for the target once the source is chosen
                    if (state.product != null) ProductSearch(query, results, stringResource(R.string.pick_target), vm::setQuery, vm::pickTarget)
                } else {
                    ProductCard(state.target!!, onChange = vm::clearTarget)
                }
                NumberField(stringResource(R.string.ops_target_qty), state.targetQty, vm::setTargetQty)
            }

            OutlinedTextField(
                value = state.reason, onValueChange = vm::setReason, label = { Text(stringResource(R.string.ops_reason)) },
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let {
                Text(
                    stringResource(
                        when (it) {
                            OpsError.INVALID -> R.string.oerr_invalid
                            OpsError.REASON -> R.string.oerr_reason
                            OpsError.STOCK -> R.string.oerr_stock
                            OpsError.SAME -> R.string.oerr_same
                            OpsError.PICK -> R.string.oerr_pick
                            OpsError.UNKNOWN -> R.string.oerr_unknown
                        }
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(onClick = vm::submit, enabled = !state.saving, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.ops_apply))
            }
        }
    }

    state.doneCost?.let { cost ->
        AlertDialog(
            onDismissRequest = { vm.consumeDone() },
            text = { Text(stringResource(R.string.ops_done_fmt, Money.format(cost))) },
            confirmButton = { TextButton(onClick = { vm.consumeDone() }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun ProductCard(product: Product, onChange: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.selected_product_fmt, product.name, formatQty(product.currentQtyMilli) + " " + stringResource(unitLabelRes(product.unit))),
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onChange) { Text(stringResource(R.string.change_action)) }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
    )
}
