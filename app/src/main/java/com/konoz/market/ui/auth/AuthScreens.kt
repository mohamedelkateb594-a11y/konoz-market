package com.konoz.market.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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

@Composable
fun LoginScreen(vm: AuthViewModel, modifier: Modifier = Modifier) {
    val ui by vm.login.collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.auth_subtitle), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = ui.username, onValueChange = vm::onUsername, singleLine = true,
            label = { Text(stringResource(R.string.auth_username)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ui.pin, onValueChange = vm::onPin, singleLine = true,
            label = { Text(stringResource(R.string.auth_pin)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        ui.error?.let { Text(errorText(it, ui.attemptsLeft, ui.lockedMinutes), color = MaterialTheme.colorScheme.error) }
        Button(onClick = vm::login, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auth_login)) }
    }
}

@Composable
fun ForcePinScreen(vm: AuthViewModel, modifier: Modifier = Modifier) {
    val ui by vm.force.collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.auth_force_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.auth_force_hint), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = ui.pin, onValueChange = vm::onForcePin, singleLine = true,
            label = { Text(stringResource(R.string.auth_new_pin)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ui.confirm, onValueChange = vm::onForceConfirm, singleLine = true,
            label = { Text(stringResource(R.string.auth_confirm_pin)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        ui.error?.let { Text(errorText(it, 0, 0), color = MaterialTheme.colorScheme.error) }
        Button(onClick = vm::saveForcedPin, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auth_save_pin)) }
    }
}

@Composable
private fun errorText(error: AuthError, attemptsLeft: Int, lockedMinutes: Int): String = when (error) {
    AuthError.INVALID ->
        if (attemptsLeft > 0) stringResource(R.string.auth_err_invalid_fmt, attemptsLeft) else stringResource(R.string.auth_err_invalid)
    AuthError.LOCKED -> stringResource(R.string.auth_err_locked_fmt, lockedMinutes)
    AuthError.PIN -> stringResource(R.string.auth_err_pin)
    AuthError.MISMATCH -> stringResource(R.string.auth_err_mismatch)
    AuthError.UNKNOWN -> stringResource(R.string.auth_err_unknown)
}
