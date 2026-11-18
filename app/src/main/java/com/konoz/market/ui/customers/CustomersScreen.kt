package com.konoz.market.ui.customers

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.data.entity.Customer
import com.konoz.market.domain.CustomerError
import com.konoz.market.domain.CustomerService
import com.konoz.market.ui.common.verticalScrollbar

/** The saved customers: search, a scrollable list with a scroll bar, add / edit / delete (delete asks first). */
@Composable
fun CustomersScreen(vm: CustomersViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
                Text(stringResource(R.string.cust_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    stringResource(R.string.cust_count_fmt, ui.all.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            OutlinedTextField(
                value = ui.query,
                onValueChange = vm::onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.cust_search)) },
                singleLine = true,
            )
            if (ui.shown.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(if (ui.all.isEmpty()) R.string.cust_empty else R.string.cust_no_match),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().verticalScrollbar(listState),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ui.shown, key = { it.id }) { customer ->
                        CustomerCard(customer, onEdit = { vm.openEdit(customer) }, onDelete = { vm.askDelete(customer) })
                    }
                }
            }
        }
        FloatingActionButton(onClick = vm::openNew, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.cust_add))
        }
    }

    ui.editor?.let { EditorDialog(it, vm) }

    ui.deleting?.let { customer ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            title = { Text(stringResource(R.string.cust_delete)) },
            text = { Text(stringResource(R.string.cust_delete_confirm_fmt, customer.name)) },
            confirmButton = {
                TextButton(onClick = vm::confirmDelete) { Text(stringResource(R.string.cust_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = vm::cancelDelete) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (ui.failed) {
        AlertDialog(
            onDismissRequest = vm::consumeFailed,
            text = { Text(stringResource(R.string.op_failed)) },
            confirmButton = { TextButton(onClick = vm::consumeFailed) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun CustomerCard(customer: Customer, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(customer.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    CustomerService.displayPhone(customer.phone),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.cust_edit)) }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cust_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun EditorDialog(editor: CustomerEditor, vm: CustomersViewModel) {
    AlertDialog(
        onDismissRequest = vm::closeEditor,
        title = { Text(stringResource(if (editor.id == 0L) R.string.cust_add else R.string.cust_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = editor.phone, onValueChange = vm::onEditorPhone, singleLine = true,
                    label = { Text(stringResource(R.string.cust_phone)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = editor.name, onValueChange = vm::onEditorName, singleLine = true,
                    label = { Text(stringResource(R.string.cust_name)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                editor.error?.let { Text(stringResource(errorText(it)), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = vm::saveEditor) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = vm::closeEditor) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun errorText(error: CustomerError): Int = when (error) {
    CustomerError.NAME_REQUIRED -> R.string.cust_err_name
    CustomerError.INVALID_PHONE -> R.string.cust_err_phone
    CustomerError.PHONE_TAKEN -> R.string.cust_err_taken
    CustomerError.NOT_FOUND, CustomerError.FAILED -> R.string.cust_err_failed
}
