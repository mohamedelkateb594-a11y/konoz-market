package com.konoz.market.ui.printer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R

@Composable
fun PrinterScreen(vm: PrinterViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.refresh() }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
            Text(stringResource(R.string.printer_title), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.busy) Text(stringResource(R.string.printer_printing), color = MaterialTheme.colorScheme.primary)
            ui.message?.let { m ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(messageRes(m)), modifier = Modifier.weight(1f),
                            color = if (m == PrinterMessage.TEST_SENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = vm::dismissMessage) { Text(stringResource(R.string.ok)) }
                    }
                }
            }

            Text(stringResource(R.string.printer_help), style = MaterialTheme.typography.bodyMedium)

            if (!ui.hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.printer_grant))
                }
            }

            Text(stringResource(R.string.printer_devices), style = MaterialTheme.typography.titleMedium)
            if (ui.devices.isEmpty()) {
                Text(stringResource(R.string.printer_no_devices), style = MaterialTheme.typography.bodyMedium)
            }
            ui.devices.forEach { d ->
                Card(onClick = { vm.select(d) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = ui.selectedAddress == d.address, onClick = { vm.select(d) })
                        Column {
                            Text(d.name, style = MaterialTheme.typography.titleMedium)
                            Text(d.address, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.printer_open_settings)) }
            TextButton(onClick = vm::refresh) { Text(stringResource(R.string.printer_refresh)) }

            Text(stringResource(R.string.printer_paper), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperWidth.values().forEach { w ->
                    FilterChip(selected = ui.width == w, onClick = { vm.setWidth(w) }, label = { Text("${w.mm} mm") })
                }
            }

            Button(onClick = vm::printTest, enabled = !ui.busy && ui.selectedAddress != null, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.printer_test))
            }
        }
    }
}

private fun messageRes(m: PrinterMessage): Int = when (m) {
    PrinterMessage.TEST_SENT -> R.string.printer_done_test
    PrinterMessage.NO_PRINTER -> R.string.printer_err_none
    PrinterMessage.NO_PERMISSION -> R.string.printer_err_permission
    PrinterMessage.BLUETOOTH_OFF -> R.string.printer_err_off
    PrinterMessage.FAILED -> R.string.printer_err_failed
}
