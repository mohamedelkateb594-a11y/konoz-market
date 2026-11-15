package com.konoz.market.ui.backup

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupScreen(vm: BackupViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val timeFmt = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.prepareRestore(uri)
    }
    var copySource by remember { mutableStateOf<LocalBackup?>(null) }
    val copyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val source = copySource
        if (uri != null && source != null) vm.copyLocalTo(source.path, uri)
        copySource = null
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
                Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge)
            }
        }
        ui.message?.let { m ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(messageRes(m)), modifier = Modifier.weight(1f),
                            color = if (isError(m)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(onClick = vm::consumeMessage) { Text(stringResource(R.string.ok)) }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_create_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.backup_create_hint), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        ui.lastBackupAt?.let { stringResource(R.string.backup_last_fmt, timeFmt.format(Date(it))) } ?: stringResource(R.string.backup_never),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { exportLauncher.launch("konoz-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".zip") },
                        enabled = !ui.busy, modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.backup_save_file)) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.backup_auto_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Switch(checked = ui.auto, onCheckedChange = vm::setAuto)
                    }
                    Text(stringResource(R.string.backup_auto_hint), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_local_title), style = MaterialTheme.typography.titleMedium)
                    if (ui.local.isEmpty()) {
                        Text(stringResource(R.string.backup_local_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ui.local.forEach { b ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(b.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                timeFmt.format(Date(b.modified)) + "  -  " + Formatter.formatShortFileSize(context, b.size),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { copySource = b; copyLauncher.launch(b.name) }, enabled = !ui.busy) {
                                    Text(stringResource(R.string.backup_local_save_copy))
                                }
                                OutlinedButton(onClick = { vm.prepareRestoreLocal(b.path) }, enabled = !ui.busy) {
                                    Text(stringResource(R.string.backup_local_restore))
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_restore_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.backup_restore_hint), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.backup_restore_choose))
                    }
                }
            }
        }
    }

    ui.pending?.let { p ->
        AlertDialog(
            onDismissRequest = vm::cancelRestore,
            title = { Text(stringResource(R.string.backup_confirm_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.backup_confirm_info_fmt,
                            timeFmt.format(Date(p.info.createdAt)), p.info.invoices, p.info.products, p.info.users, p.info.receiptFiles, p.info.imageFiles,
                        ),
                    )
                    Text(stringResource(R.string.backup_confirm_body), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(
                    onClick = vm::confirmRestore, enabled = !ui.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.backup_confirm_btn)) }
            },
            dismissButton = { TextButton(onClick = vm::cancelRestore, enabled = !ui.busy) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun isError(m: BackupMessage) = m != BackupMessage.SAVED && m != BackupMessage.COPIED && m != BackupMessage.RESTORED

private fun messageRes(m: BackupMessage): Int = when (m) {
    BackupMessage.SAVED -> R.string.backup_msg_saved
    BackupMessage.COPIED -> R.string.backup_msg_copied
    BackupMessage.RESTORED -> R.string.backup_msg_restored
    BackupMessage.NOT_BACKUP -> R.string.backup_err_not_backup
    BackupMessage.VERSION -> R.string.backup_err_version
    BackupMessage.CORRUPT -> R.string.backup_err_corrupt
    BackupMessage.NO_ADMIN -> R.string.backup_err_no_admin
    BackupMessage.FAILED -> R.string.backup_err_failed
}
