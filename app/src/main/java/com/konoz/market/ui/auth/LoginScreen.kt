package com.konoz.market.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    val form by vm.form.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.auth_login_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = form.username, onValueChange = vm::editUsername, singleLine = true,
            label = { Text(stringResource(R.string.auth_username)) }, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = form.pin, onValueChange = vm::editPin, singleLine = true,
            label = { Text(stringResource(R.string.auth_pin)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(if (it == LoginError.CREDENTIALS) R.string.auth_err_credentials else R.string.auth_err_unknown),
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = vm::login, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(stringResource(R.string.auth_login_btn))
        }
    }
}
