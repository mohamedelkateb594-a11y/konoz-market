package com.konoz.market.ui.suppliers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.domain.StatementLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatementScreen(
    vm: StatementViewModel,
    onNewPurchase: () -> Unit,
    onNewReturn: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val supplier by vm.supplier.collectAsStateWithLifecycle()
    val lines by vm.lines.collectAsStateWithLifecycle()
    val balance by vm.balance.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var settling by remember { mutableStateOf<StatementLine?>(null) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
            Column {
                Text(stringResource(R.string.statement_title), style = MaterialTheme.typography.titleLarge)
                Text(supplier?.name.orEmpty(), style = MaterialTheme.typography.bodyLarge)
            }
        }

        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp)) { BalanceText(balance) }
        }

        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNewPurchase, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.new_purchase)) }
            OutlinedButton(onClick = onNewReturn, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.new_purchase_return)) }
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(lines.asReversed(), key = { it.invoice.id }) { line ->
                val inv = line.invoice
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val title = if (inv.isReturn) stringResource(R.string.return_label_fmt, (inv.number ?: 0L).toString())
                        else stringResource(R.string.invoice_label_fmt, (inv.number ?: 0L).toString())
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(fmt.format(Date(inv.approvedAt ?: inv.createdAt)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(R.string.col_total_fmt, Money.format(inv.total)) + "  \u2022  " +
                                stringResource(R.string.col_paid_fmt, Money.format(inv.paid)) + "  \u2022  " +
                                stringResource(R.string.col_remaining_fmt, Money.format(inv.remaining)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (inv.remaining > 0L) {
                            TextButton(onClick = { settling = line }) {
                                Text(stringResource(if (inv.isReturn) R.string.collect_refund else R.string.settle))
                            }
                        }
                    }
                }
            }
        }
    }

    settling?.let { line ->
        SettleDialog(
            isReturn = line.invoice.isReturn,
            defaultAmount = line.invoice.remaining,
            onConfirm = { vm.settle(line.invoice.id, it); settling = null },
            onDismiss = { settling = null },
        )
    }

    notice?.let { n ->
        AlertDialog(
            onDismissRequest = { vm.consumeNotice() },
            text = {
                Text(
                    stringResource(
                        when (n) {
                            StatementNotice.SETTLED -> R.string.settled_ok
                            StatementNotice.INVALID_AMOUNT -> R.string.err_amount
                            StatementNotice.FAILED -> R.string.op_failed
                        }
                    )
                )
            },
            confirmButton = { TextButton(onClick = { vm.consumeNotice() }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun SettleDialog(isReturn: Boolean, defaultAmount: Long, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(Money.fromMinor(defaultAmount).toPlainString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isReturn) R.string.collect_title else R.string.settle_title)) },
        text = {
            OutlinedTextField(
                value = amount, onValueChange = { amount = it }, label = { Text(stringResource(R.string.settle_amount)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(amount) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
