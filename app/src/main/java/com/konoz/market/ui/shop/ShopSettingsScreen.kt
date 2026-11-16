package com.konoz.market.ui.shop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R

@Composable
fun ShopSettingsScreen(vm: ShopSettingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
                Text(stringResource(R.string.shop_title), style = MaterialTheme.typography.titleLarge)
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ui.shopName,
                        onValueChange = vm::onShopName,
                        label = { Text(stringResource(R.string.shop_name_label)) },
                        supportingText = { Text(stringResource(R.string.shop_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = vm::saveShopName) { Text(stringResource(R.string.shop_name_save)) }
                }
            }
        }
        ui.message?.let { m ->
            item {
                val text = when (m) {
                    ShopMessage.SAVED -> R.string.shop_saved
                    ShopMessage.NAME_TAKEN -> R.string.shop_category_taken
                    ShopMessage.BLANK -> R.string.shop_category_blank
                }
                Text(
                    stringResource(text),
                    color = if (m == ShopMessage.SAVED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.shop_categories), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.shop_categories_hint), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = ui.newCategory,
                    onValueChange = vm::onNewCategory,
                    label = { Text(stringResource(R.string.shop_category_new)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = vm::addCategory) { Text(stringResource(R.string.shop_category_add)) }
            }
        }
        if (categories.isEmpty()) {
            item { Text(stringResource(R.string.shop_categories_empty)) }
        }
        items(categories, key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.startRename(c) }) { Text(stringResource(R.string.shop_category_rename)) }
                    TextButton(onClick = { vm.askRemove(c) }) {
                        Text(stringResource(R.string.shop_category_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    ui.renaming?.let {
        AlertDialog(
            onDismissRequest = vm::cancelRename,
            title = { Text(stringResource(R.string.shop_category_rename_title)) },
            text = {
                OutlinedTextField(value = ui.renameText, onValueChange = vm::onRenameText, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(onClick = vm::confirmRename) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = vm::cancelRename) { Text(stringResource(R.string.cancel)) } },
        )
    }
    ui.removing?.let { c ->
        AlertDialog(
            onDismissRequest = vm::cancelRemove,
            title = { Text(stringResource(R.string.shop_category_remove_title)) },
            text = { Text(stringResource(R.string.shop_category_remove_msg, c.name)) },
            confirmButton = { TextButton(onClick = vm::confirmRemove) { Text(stringResource(R.string.shop_category_remove)) } },
            dismissButton = { TextButton(onClick = vm::cancelRemove) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
