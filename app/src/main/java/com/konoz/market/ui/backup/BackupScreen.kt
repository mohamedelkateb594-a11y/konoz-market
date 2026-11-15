package com.konoz.market.ui.backup

import android.text.format.Formatter
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import java.text.DateFormat
import java.util.Date

@Composable
fun BackupScreen(vm: BackupViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.pickForRestore(uri)
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
            Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.busy) Text(stringResource(R.string.backup_working), color = MaterialTheme.colorScheme.primary)
            ui.message?.let { m ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        val ok = m == BackupMessage.EXPORTED || m == BackupMessage.LOCAL_SAVED
                        Text(
                            stringResource(messageRes(m)), modifier = Modifier.weight(1f),
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = vm::dismissMessage) { Text(stringResource(R.string.ok)) }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_export_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.backup_export_desc), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { exportLauncher.launch(vm.suggestedName()) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.backup_export_btn))
                    }
                    OutlinedButton(onClick = vm::saveLocal, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.backup_local_btn))
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.backup_restore_desc), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.backup_restore_btn))
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.backup_auto_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.backup_auto_desc), style = MaterialTheme.typography.bodyMedium)
                        }
                        Switch(checked = ui.autoEnabled, onCheckedChange = vm::setAuto)
                    }
                }
            }

            Text(stringResource(R.string.backup_local_title), style = MaterialTheme.typography.titleMedium)
            if (ui.local.isEmpty()) {
                Text(stringResource(R.string.backup_local_empty), style = MaterialTheme.typography.bodyMedium)
            }
            ui.local.forEach { b ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dateFormat.format(Date(b.modifiedAt)), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.backup_item_fmt, stringResource(kindRes(b.name)), Formatter.formatShortFileSize(context, b.sizeBytes)),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        TextButton(onClick = { vm.pickLocalForRestore(b.name) }, enabled = !ui.busy) { Text(stringResource(R.string.backup_restore_item)) }
                    }
                }
            }
        }
    }

    ui.pending?.let { p ->
        AlertDialog(
            onDismissRequest = vm::dismissPending,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_confirm_body_fmt,
                        dateFormat.format(Date(p.manifest.createdAt)), p.manifest.salesInvoiceCount,
                    ),
                )
            },
            confirmButton = { Button(onClick = vm::confirmRestore) { Text(stringResource(R.string.backup_confirm_ok)) } },
            dismissButton = { TextButton(onClick = vm::dismissPending) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun messageRes(m: BackupMessage): Int = when (m) {
    BackupMessage.EXPORTED -> R.string.backup_done_export
    BackupMessage.LOCAL_SAVED -> R.string.backup_done_local
    BackupMessage.NOT_A_BACKUP -> R.string.backup_err_not_backup
    BackupMessage.CORRUPT -> R.string.backup_err_corrupt
    BackupMessage.NEWER -> R.string.backup_err_newer
    BackupMessage.IO -> R.string.backup_err_io
    BackupMessage.NOT_ALLOWED -> R.string.backup_err_not_allowed
}

private fun kindRes(name: String): Int = when {
    name.contains("_auto_") -> R.string.backup_kind_auto
    name.contains("_pre_restore_") -> R.string.backup_kind_pre_restore
    else -> R.string.backup_kind_manual
}
