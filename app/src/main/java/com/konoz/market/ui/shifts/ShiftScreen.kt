package com.konoz.market.ui.shifts

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.domain.ShiftSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ShiftScreen(vm: ShiftViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.openShift.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val openForm by vm.openForm.collectAsStateWithLifecycle()
    val closeForm by vm.closeForm.collectAsStateWithLifecycle()
    val detail by vm.detail.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val fmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
            Text(stringResource(R.string.shift_title), style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { CurrentShiftCard(state, fmt, onOpen = vm::showOpenForm, onClose = vm::showCloseForm) }
            item { Text(stringResource(R.string.shift_history), style = MaterialTheme.typography.titleMedium) }
            if (history.isEmpty()) {
                item {
                    Text(stringResource(R.string.shift_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(history, key = { it.id }) { s ->
                    Card(onClick = { vm.showDetail(s.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                fmt.format(Date(s.openedAt)) + "  →  " + (s.closedAt?.let { fmt.format(Date(it)) } ?: ""),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            val diff = s.difference
                            if (diff != null) {
                                Text(differenceText(diff), color = differenceColor(diff), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }

    openForm?.let { text ->
        AlertDialog(
            onDismissRequest = vm::dismissOpenForm,
            title = { Text(stringResource(R.string.shift_open_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text, onValueChange = vm::editOpenForm, singleLine = true,
                        label = { Text(stringResource(R.string.shift_opening_cash)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(stringResource(errorRes(it)), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { Button(onClick = vm::open, enabled = !saving) { Text(stringResource(R.string.shift_open_btn)) } },
            dismissButton = { TextButton(onClick = vm::dismissOpenForm) { Text(stringResource(R.string.cancel)) } },
        )
    }

    closeForm?.let { text ->
        val expected = (state as? OpenShiftState.Open)?.summary?.expectedCash ?: 0L
        val counted = Money.parseToMinor(text)
        AlertDialog(
            onDismissRequest = vm::dismissCloseForm,
            title = { Text(stringResource(R.string.shift_close_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.shift_close_hint))
                    Text(stringResource(R.string.shift_expected_fmt, Money.format(expected)), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = text, onValueChange = vm::editCloseForm, singleLine = true,
                        label = { Text(stringResource(R.string.shift_actual)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                    )
                    if (counted != null && counted >= 0L) {
                        val diff = counted - expected
                        Text(differenceText(diff), color = differenceColor(diff))
                    }
                    error?.let { Text(stringResource(errorRes(it)), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { Button(onClick = vm::close, enabled = !saving) { Text(stringResource(R.string.shift_close_btn)) } },
            dismissButton = { TextButton(onClick = vm::dismissCloseForm) { Text(stringResource(R.string.cancel)) } },
        )
    }

    detail?.let { s ->
        AlertDialog(
            onDismissRequest = vm::dismissDetail,
            title = { Text(stringResource(R.string.shift_report_title)) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { SummaryLines(s, fmt) } },
            confirmButton = { Button(onClick = vm::dismissDetail) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun CurrentShiftCard(state: OpenShiftState, fmt: SimpleDateFormat, onOpen: () -> Unit, onClose: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                OpenShiftState.Loading -> Unit
                OpenShiftState.None -> {
                    Text(stringResource(R.string.shift_none), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.shift_none_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.shift_open_btn)) }
                }
                is OpenShiftState.Open -> {
                    Text(stringResource(R.string.shift_current), style = MaterialTheme.typography.titleMedium)
                    SummaryLines(state.summary, fmt)
                    Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.shift_close_btn)) }
                }
            }
        }
    }
}

/** The cash-drawer figures of a shift, top to bottom: opening, cash in/out, expected, counted + difference, other payment methods. */
@Composable
private fun SummaryLines(s: ShiftSummary, fmt: SimpleDateFormat) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Line(R.string.shift_opened_at, fmt.format(Date(s.shift.openedAt)))
        s.shift.closedAt?.let { Line(R.string.shift_closed_at, fmt.format(Date(it))) }
        Line(R.string.shift_opening_cash, Money.format(s.openingCash))
        Line(R.string.shift_cash_sales, Money.format(s.cashSales))
        Line(R.string.shift_cash_refunds, Money.format(s.cashRefunds))
        Line(R.string.shift_cash_expenses, Money.format(s.cashExpenses))
        Line(R.string.shift_supplier_payments, Money.format(s.supplierPayments))
        Line(R.string.shift_expected, Money.format(s.expectedCash), bold = true)
        val actual = s.actualCash
        val diff = s.difference
        if (actual != null) Line(R.string.shift_actual, Money.format(actual), bold = true)
        if (diff != null) Line(R.string.shift_difference, differenceText(diff), bold = true, color = differenceColor(diff))
        Line(R.string.shift_card_sales, Money.format(s.cardSales))
        Line(R.string.shift_wallet_sales, Money.format(s.walletSales))
        Line(R.string.shift_total_sales, Money.format(s.totalSales), bold = true)
    }
}

@Composable
private fun Line(@StringRes label: Int, value: String, bold: Boolean = false, color: Color = Color.Unspecified) {
    val style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = style, modifier = Modifier.weight(1f))
        Text(value, style = style, color = color)
    }
}

@Composable
private fun differenceText(diff: Long): String = when {
    diff < 0L -> stringResource(R.string.shift_diff_short_fmt, Money.format(-diff))
    diff > 0L -> stringResource(R.string.shift_diff_over_fmt, Money.format(diff))
    else -> stringResource(R.string.shift_diff_even)
}

/** A shortage is shown in the error color; a balanced or surplus drawer in the primary color. */
@Composable
private fun differenceColor(diff: Long): Color =
    if (diff < 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

@StringRes
private fun errorRes(e: ShiftError): Int = when (e) {
    ShiftError.AMOUNT -> R.string.serr_amount
    ShiftError.ALREADY_OPEN -> R.string.serr_already_open
    ShiftError.NOT_OPEN -> R.string.serr_not_open
    ShiftError.UNKNOWN -> R.string.serr_unknown
}
