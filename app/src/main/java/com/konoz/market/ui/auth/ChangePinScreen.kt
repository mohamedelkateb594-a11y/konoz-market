package com.konoz.market.ui.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R

/**
 * [forced] = the first-run screen: no back button, nothing else is reachable until the PIN is changed.
 * [onBack] is null in that case.
 */
@Composable
fun ChangePinScreen(
    vm: ChangePinViewModel,
    forced: Boolean,
    onDone: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val form by vm.form.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val done by vm.done.collectAsStateWithLifecycle()
    LaunchedEffect(done) { if (done) onDone() }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel)) }
            }
            Text(stringResource(R.string.auth_pin_title), style = MaterialTheme.typography.titleLarge)
        }
        if (forced) Text(stringResource(R.string.auth_pin_forced_hint), style = MaterialTheme.typography.bodyLarge)
        PinField(form.current, R.string.auth_pin_current) { v -> vm.edit { it.copy(current = v) } }
        PinField(form.newPin, R.string.auth_pin_new) { v -> vm.edit { it.copy(newPin = v) } }
        PinField(form.confirm, R.string.auth_pin_confirm) { v -> vm.edit { it.copy(confirm = v) } }
        error?.let { Text(stringResource(pinErrorRes(it)), color = MaterialTheme.colorScheme.error) }
        Button(onClick = vm::save, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.auth_pin_save))
        }
    }
}

@Composable
private fun PinField(value: String, @StringRes label: Int, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(8)) }, singleLine = true,
        label = { Text(stringResource(label)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@StringRes
private fun pinErrorRes(e: PinError): Int = when (e) {
    PinError.FORMAT -> R.string.auth_pin_err_format
    PinError.WRONG_OLD -> R.string.auth_pin_err_wrong_old
    PinError.UNCHANGED -> R.string.auth_pin_err_unchanged
    PinError.DEFAULT -> R.string.auth_pin_err_default
    PinError.MISMATCH -> R.string.auth_pin_err_mismatch
    PinError.UNKNOWN -> R.string.auth_pin_err_unknown
}
