package com.konoz.market.ui.suppliers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.konoz.market.data.entity.Supplier

@Composable
fun SuppliersScreen(
    vm: SuppliersViewModel,
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Supplier?>(null) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
                Text(stringResource(R.string.suppliers_title), style = MaterialTheme.typography.titleLarge)
            }
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.search_suppliers)) },
                singleLine = true,
            )
            if (rows.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_suppliers), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rows, key = { it.supplier.id }) { row ->
                        Card(onClick = { onOpen(row.supplier.id) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(row.supplier.name, style = MaterialTheme.typography.titleMedium)
                                    row.supplier.phone?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    BalanceText(row.balance)
                                }
                                IconButton(onClick = { editing = row.supplier; showDialog = true }) {
                                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit_supplier))
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = { editing = null; showDialog = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_supplier))
        }
    }

    if (showDialog) {
        val target = editing
        SupplierDialog(
            initial = target,
            onSave = { name, phone, address, notes -> vm.save(target?.id ?: 0L, name, phone, address, notes); showDialog = false },
            onDelete = if (target == null) null else ({ vm.delete(target.id); showDialog = false }),
            onDismiss = { showDialog = false },
        )
    }

    notice?.let { n ->
        AlertDialog(
            onDismissRequest = { vm.consumeNotice() },
            text = {
                Text(
                    stringResource(if (n == SuppliersNotice.DELETE_BLOCKED) R.string.supplier_delete_blocked else R.string.op_failed)
                )
            },
            confirmButton = { TextButton(onClick = { vm.consumeNotice() }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

/** Balance line: red when we owe the supplier, normal when they owe us, plain when settled. */
@Composable
fun BalanceText(balance: Long, modifier: Modifier = Modifier) {
    val text = when {
        balance > 0L -> stringResource(R.string.balance_we_owe_fmt, Money.format(balance))
        balance < 0L -> stringResource(R.string.balance_they_owe_fmt, Money.format(-balance))
        else -> stringResource(R.string.balance_settled)
    }
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium,
        color = if (balance > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SupplierDialog(
    initial: Supplier?,
    onSave: (String, String, String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var phone by remember { mutableStateOf(initial?.phone.orEmpty()) }
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.add_supplier else R.string.edit_supplier)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.supplier_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    phone, { phone = it }, label = { Text(stringResource(R.string.supplier_phone)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(address, { address = it }, label = { Text(stringResource(R.string.supplier_address)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.supplier_notes)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { onSave(name, phone, address, notes) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
