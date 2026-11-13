package com.konoz.market.ui.shifts

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.core.Money
import com.konoz.market.data.entity.Shift
import com.konoz.market.domain.ShiftReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ShiftDialog { OPEN, CLOSE }

@Composable
fun ShiftScreen(vm: ShiftViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<ShiftDialog?>(null) }
    val timeFmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale.US) }
    val current = ui.current

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
                Text(stringResource(R.string.shift_title), style = MaterialTheme.typography.titleLarge)
            }
        }
        ui.message?.let { m ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(messageText(m), modifier = Modifier.weight(1f), color = if (isError(m)) MaterialTheme.colorScheme.error else Color.Unspecified)
                        TextButton(onClick = vm::consumeMessage) { Text(stringResource(R.string.ok)) }
                    }
                }
            }
        }
        if (!ui.loaded) {
            item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (current == null) {
                            Text(stringResource(R.string.shift_none_open), style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { dialog = ShiftDialog.OPEN }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.shift_open)) }
                        } else {
                            Text(stringResource(R.string.shift_current), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.shift_opened_at_fmt, timeFmt.format(Date(current.shift.openedAt))), style = MaterialTheme.typography.bodyMedium)
                            ReportLines(current)
                            Button(onClick = { dialog = ShiftDialog.CLOSE }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.shift_close)) }
                        }
                    }
                }
            }
            item { Text(stringResource(R.string.shift_history), style = MaterialTheme.typography.titleMedium) }
            if (ui.history.isEmpty()) {
                item { Text(stringResource(R.string.shift_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(ui.history, key = { it.id }) { s -> HistoryRow(s, timeFmt) { vm.select(s.id) } }
            }
        }
    }

    when (dialog) {
        ShiftDialog.OPEN -> AmountDialog(
            title = R.string.shift_open,
            hint = stringResource(R.string.shift_opening_cash),
            confirm = R.string.shift_open,
            onConfirm = { vm.open(it); dialog = null },
            onDismiss = { dialog = null },
        )
        ShiftDialog.CLOSE -> AmountDialog(
            title = R.string.shift_close,
            hint = stringResource(R.string.shift_expected_hint_fmt, Money.format(current?.expectedCash ?: 0L)),
            confirm = R.string.shift_close,
            onConfirm = { vm.close(it); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }

    ui.selected?.let { report ->
        AlertDialog(
            onDismissRequest = vm::clearSelection,
            title = { Text(stringResource(R.string.shift_report_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.shift_opened_at_fmt, timeFmt.format(Date(report.shift.openedAt))) +
                            (report.shift.closedAt?.let { "  -  " + stringResource(R.string.shift_closed_at_fmt, timeFmt.format(Date(it))) } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    ReportLines(report)
                }
            },
            confirmButton = { TextButton(onClick = vm::clearSelection) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun ReportLines(r: ShiftReport) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Line(stringResource(R.string.shift_opening_cash), Money.format(r.shift.openingCash))
        Line(stringResource(R.string.shift_cash_sales), Money.format(r.cashSales))
        Line(stringResource(R.string.shift_other_payments), Money.format(r.otherPayments))
        Line(stringResource(R.string.shift_cash_expenses), Money.format(r.cashExpenses))
        Line(stringResource(R.string.shift_cash_refunds), Money.format(r.cashRefunds))
        Line(stringResource(R.string.shift_expected), Money.format(r.expectedCash), strong = true)
        r.actualCash?.let { Line(stringResource(R.string.shift_actual), Money.format(it), strong = true) }
        r.difference?.let { Line(stringResource(R.string.shift_difference), differenceText(it), strong = true, color = differenceColor(it)) }
    }
}

@Composable
private fun HistoryRow(s: Shift, timeFmt: SimpleDateFormat, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(timeFmt.format(Date(s.openedAt)) + "  ->  " + (s.closedAt?.let { timeFmt.format(Date(it)) } ?: ""), style = MaterialTheme.typography.bodyMedium)
            s.difference?.let {
                Text(stringResource(R.string.shift_difference) + ": " + differenceText(it), style = MaterialTheme.typography.titleSmall, color = differenceColor(it))
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, strong: Boolean = false, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
        Text(value, style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun AmountDialog(title: Int, hint: String, confirm: Int, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(hint) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { Button(onClick = { onConfirm(text) }) { Text(stringResource(confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Shortage is shown with a minus, overage with a plus. */
private fun differenceText(d: Long): String = (if (d > 0) "+" else "") + Money.format(d)

@Composable
private fun differenceColor(d: Long): Color = if (d < 0) MaterialTheme.colorScheme.error else Color.Unspecified

private fun isError(m: ShiftMessage) = m is ShiftMessage.AlreadyOpen || m is ShiftMessage.NoOpenShift || m is ShiftMessage.InvalidAmount || m is ShiftMessage.Failed

@Composable
private fun messageText(m: ShiftMessage): String = when (m) {
    ShiftMessage.Opened -> stringResource(R.string.shift_msg_opened)
    is ShiftMessage.Closed -> when {
        m.difference == 0L -> stringResource(R.string.shift_msg_closed_exact)
        m.difference < 0 -> stringResource(R.string.shift_msg_closed_short_fmt, Money.format(-m.difference))
        else -> stringResource(R.string.shift_msg_closed_over_fmt, Money.format(m.difference))
    }
    ShiftMessage.AlreadyOpen -> stringResource(R.string.shift_err_already_open)
    ShiftMessage.NoOpenShift -> stringResource(R.string.shift_err_none)
    ShiftMessage.InvalidAmount -> stringResource(R.string.shift_err_amount)
    ShiftMessage.Failed -> stringResource(R.string.shift_err_failed)
}
