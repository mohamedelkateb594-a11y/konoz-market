package com.konoz.market.ui.reports

import androidx.compose.foundation.layout.height
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.konoz.market.R

/** Pick the first and last day of a custom report period. OK is enabled once both days are chosen (one day = same day twice). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRangeDialog(onDismiss: () -> Unit, onConfirm: (fromPicked: Long, toPicked: Long) -> Unit) {
    val state = rememberDateRangePickerState()
    val from = state.selectedStartDateMillis
    val to = state.selectedEndDateMillis ?: from
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { if (from != null && to != null) onConfirm(from, to) }, enabled = from != null) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(480.dp))
    }
}
