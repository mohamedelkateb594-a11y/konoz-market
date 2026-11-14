package com.konoz.market.ui.users

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.data.entity.User
import com.konoz.market.data.entity.UserRole

@Composable
fun UsersScreen(vm: UsersViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
            Text(stringResource(R.string.users_title), style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Button(onClick = vm::showAdd, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.users_add)) } }
            items(ui.users, key = { it.id }) { u ->
                Card(onClick = { vm.openActions(u) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(u.name, style = MaterialTheme.typography.titleMedium)
                        val state = if (u.isActive) "" else "  -  " + stringResource(R.string.users_inactive)
                        Text("@" + u.username + "  -  " + stringResource(roleLabel(u.role)) + state, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    ui.add?.let { form ->
        AlertDialog(
            onDismissRequest = vm::dismissAdd,
            title = { Text(stringResource(R.string.users_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.name, onValueChange = { v -> vm.editAdd { it.copy(name = v) } }, singleLine = true,
                        label = { Text(stringResource(R.string.users_name)) }, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.username, onValueChange = { v -> vm.editAdd { it.copy(username = v) } }, singleLine = true,
                        label = { Text(stringResource(R.string.auth_username)) }, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.pin, onValueChange = { v -> vm.editAdd { it.copy(pin = v.filter(Char::isDigit).take(8)) } }, singleLine = true,
                        label = { Text(stringResource(R.string.auth_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UserRole.values().forEach { r ->
                            FilterChip(selected = form.role == r, onClick = { vm.editAdd { it.copy(role = r) } }, label = { Text(stringResource(roleLabel(r))) })
                        }
                    }
                    ui.error?.let { Text(stringResource(errorRes(it)), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { Button(onClick = vm::submitAdd, enabled = !ui.busy) { Text(stringResource(R.string.users_save)) } },
            dismissButton = { TextButton(onClick = vm::dismissAdd) { Text(stringResource(R.string.cancel)) } },
        )
    }

    ui.actionsFor?.let { u -> ActionsDialog(u, ui.busy, ui.error, vm) }

    ui.resetFor?.let { u ->
        AlertDialog(
            onDismissRequest = vm::dismissReset,
            title = { Text(stringResource(R.string.users_reset_pin) + ": " + u.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ui.resetPin, onValueChange = vm::editResetPin, singleLine = true,
                        label = { Text(stringResource(R.string.auth_new_pin)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth(),
                    )
                    ui.error?.let { Text(stringResource(errorRes(it)), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { Button(onClick = vm::submitReset, enabled = !ui.busy) { Text(stringResource(R.string.users_save)) } },
            dismissButton = { TextButton(onClick = vm::dismissReset) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ActionsDialog(user: User, busy: Boolean, error: UsersError?, vm: UsersViewModel) {
    AlertDialog(
        onDismissRequest = vm::dismissActions,
        title = { Text(user.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.openReset(user) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.users_reset_pin)) }
                OutlinedButton(onClick = { vm.toggleActive(user) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (user.isActive) R.string.users_deactivate else R.string.users_activate))
                }
                Text(stringResource(R.string.users_role), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UserRole.values().forEach { r ->
                        FilterChip(
                            selected = user.role == r,
                            onClick = { if (user.role != r) vm.changeRole(user, r) },
                            enabled = !busy,
                            label = { Text(stringResource(roleLabel(r))) },
                        )
                    }
                }
                error?.let { Text(stringResource(errorRes(it)), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = vm::dismissActions) { Text(stringResource(R.string.ok)) } },
    )
}

private fun roleLabel(r: UserRole): Int = when (r) {
    UserRole.ADMIN -> R.string.role_admin
    UserRole.CASHIER -> R.string.role_cashier
    UserRole.STOCK_KEEPER -> R.string.role_stock_keeper
}

private fun errorRes(e: UsersError): Int = when (e) {
    UsersError.NAME -> R.string.uerr_name
    UsersError.USERNAME -> R.string.uerr_username
    UsersError.TAKEN -> R.string.uerr_taken
    UsersError.PIN -> R.string.uerr_pin
    UsersError.LAST_ADMIN -> R.string.uerr_last_admin
    UsersError.NOT_ALLOWED -> R.string.uerr_not_allowed
    UsersError.UNKNOWN -> R.string.uerr_unknown
}
