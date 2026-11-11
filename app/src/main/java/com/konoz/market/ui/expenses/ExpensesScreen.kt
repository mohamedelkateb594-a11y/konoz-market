package com.konoz.market.ui.expenses

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.data.entity.ExpenseCategory
import com.konoz.market.domain.PeriodKind
import com.konoz.market.ui.common.ProductImage
import com.konoz.market.ui.common.ReceiptImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExpensesScreen(vm: ExpensesViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val period by vm.period.collectAsStateWithLifecycle()
    val list by vm.list.collectAsStateWithLifecycle()
    val total by vm.periodTotal.collectAsStateWithLifecycle()
    val day by vm.dayTotal.collectAsStateWithLifecycle()
    val month by vm.monthTotal.collectAsStateWithLifecycle()
    val year by vm.yearTotal.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
                Text(stringResource(R.string.expenses_title), style = MaterialTheme.typography.titleLarge)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val kinds = listOf(PeriodKind.DAY to R.string.period_day, PeriodKind.MONTH to R.string.period_month, PeriodKind.YEAR to R.string.period_year)
                items(kinds) { (kind, label) ->
                    FilterChip(selected = period == kind, onClick = { vm.setPeriod(kind) }, label = { Text(stringResource(label)) })
                }
            }
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.expenses_total_fmt, Money.format(total)), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.exp_day_fmt, Money.format(day)) + "  \u2022  " +
                            stringResource(R.string.exp_month_fmt, Money.format(month)) + "  \u2022  " +
                            stringResource(R.string.exp_year_fmt, Money.format(year)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.exp_no_items), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { e ->
                        Card(onClick = { vm.openEdit(e) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (e.receiptPath != null) ProductImage(e.receiptPath, 48.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(e.description, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(categoryLabel(e.category)) + "  \u2022  " + fmt.format(Date(e.createdAt)),
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (!e.paidInCash) Text(stringResource(R.string.exp_not_cash), style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(Money.format(e.amount), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = vm::openNew, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_expense))
        }
    }

    form?.let { f ->
        ExpenseDialog(
            form = f, error = error, saving = saving, onChange = vm::editForm, onSave = vm::save,
            onDelete = if (f.id != 0L) ({ confirmDelete = f.id }) else null, onDismiss = vm::closeForm,
        )
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.exp_delete_title)) },
            text = { Text(stringResource(R.string.exp_delete_body)) },
            confirmButton = { Button(onClick = { vm.delete(id); confirmDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ExpenseDialog(
    form: ExpenseForm,
    error: ExpenseError?,
    saving: Boolean,
    onChange: ((ExpenseForm) -> ExpenseForm) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) { ReceiptImages.save(context, uri) }
            if (path != null) onChange { it.copy(receiptPath = path) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (form.id == 0L) R.string.add_expense else R.string.edit_expense)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = form.amount, onValueChange = { v -> onChange { it.copy(amount = v) } },
                    label = { Text(stringResource(R.string.exp_amount)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.exp_category), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ExpenseCategory.values().toList()) { c ->
                        FilterChip(selected = form.category == c, onClick = { onChange { it.copy(category = c) } }, label = { Text(stringResource(categoryLabel(c))) })
                    }
                }
                OutlinedTextField(
                    value = form.description, onValueChange = { v -> onChange { it.copy(description = v) } },
                    label = { Text(stringResource(R.string.exp_description)) }, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.notes, onValueChange = { v -> onChange { it.copy(notes = v) } },
                    label = { Text(stringResource(R.string.exp_notes)) }, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.exp_paid_cash), modifier = Modifier.weight(1f))
                    Switch(checked = form.paidInCash, onCheckedChange = { v -> onChange { it.copy(paidInCash = v) } })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProductImage(form.receiptPath, 64.dp)
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(stringResource(R.string.exp_choose_receipt))
                    }
                    if (form.receiptPath != null) {
                        TextButton(onClick = { onChange { it.copy(receiptPath = null) } }) { Text(stringResource(R.string.exp_remove_receipt)) }
                    }
                }
                error?.let {
                    Text(
                        stringResource(
                            when (it) {
                                ExpenseError.AMOUNT -> R.string.eerr_amount
                                ExpenseError.DESCRIPTION -> R.string.eerr_description
                                ExpenseError.UNKNOWN -> R.string.eerr_unknown
                            }
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onSave, enabled = !saving) { Text(stringResource(R.string.save)) } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private fun categoryLabel(c: ExpenseCategory): Int = when (c) {
    ExpenseCategory.ELECTRICITY -> R.string.cat_electricity
    ExpenseCategory.WATER -> R.string.cat_water
    ExpenseCategory.RENT -> R.string.cat_rent
    ExpenseCategory.WAGES -> R.string.cat_wages
    ExpenseCategory.TRANSPORT -> R.string.cat_transport
    ExpenseCategory.MAINTENANCE -> R.string.cat_maintenance
    ExpenseCategory.OPERATING_PURCHASES -> R.string.cat_operating_purchases
    ExpenseCategory.MISC -> R.string.cat_misc
}
