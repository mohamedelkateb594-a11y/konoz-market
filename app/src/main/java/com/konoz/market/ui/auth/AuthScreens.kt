package com.konoz.market.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R
import com.konoz.market.ui.theme.KonozTheme_

// Login look: simple, modern, centered. A soft decorative shape in the corners, the brand mark (a shopping bag) with the
// program name "تاجر" as a heavy wordmark, then two pill fields and one accent pill button.
// Every color comes from the theme tokens (KonozTheme_.colors / MaterialTheme.colorScheme), so it follows light and dark mode.

@Composable
fun LoginScreen(vm: AuthViewModel, modifier: Modifier = Modifier) {
    val ui by vm.login.collectAsStateWithLifecycle()
    val tokens = KonozTheme_.colors
    val scheme = MaterialTheme.colorScheme
    val accent = tokens.signInAccent
    val soft = tokens.signInHeader

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent,
        unfocusedBorderColor = scheme.outline,
        focusedContainerColor = scheme.surface,
        unfocusedContainerColor = scheme.surface,
        cursorColor = accent,
    )
    val pill = RoundedCornerShape(50)

    Box(modifier.fillMaxSize().background(scheme.background).imePadding()) {
        // soft decorative circles, top end and bottom start
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.62f
            drawCircle(soft, r, Offset(size.width, 0f))
            drawCircle(soft, r * 0.7f, Offset(0f, size.height))
        }

        Column(
            Modifier.align(Alignment.Center).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 36.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BrandMark(accent = accent, onAccent = tokens.onSignInAccent, soft = soft)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.login_brand_name),
                style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp, fontWeight = FontWeight.Black),
                color = accent,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.login_brand_latin),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 8.sp, fontWeight = FontWeight.Bold),
                color = scheme.onSurfaceVariant,
            )
            Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(50)).background(accent))
            Text(
                stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.username, onValueChange = vm::onUsername, singleLine = true,
                placeholder = { Text(stringResource(R.string.auth_username)) },
                shape = pill, colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = ui.pin, onValueChange = vm::onPin, singleLine = true,
                placeholder = { Text(stringResource(R.string.auth_pin)) },
                visualTransformation = PasswordVisualTransformation(),
                shape = pill, colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (!ui.busy) vm.login() }),
                modifier = Modifier.fillMaxWidth(),
            )
            ui.error?.let { Text(errorText(it, ui.attemptsLeft, ui.lockedMinutes), color = scheme.error, textAlign = TextAlign.Center) }
            Button(
                onClick = vm::login,
                enabled = !ui.busy,
                shape = pill,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = tokens.onSignInAccent),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.auth_login), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** The brand mark: a rounded accent square with a shopping bag (handle, body, a coin on it). Drawn from shapes, no image file. */
@Composable
private fun BrandMark(accent: Color, onAccent: Color, soft: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(92.dp).clip(RoundedCornerShape(30.dp)).background(accent), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(52.dp)) {
            val w = size.width
            val h = size.height
            drawArc(
                onAccent, 180f, 180f, false,
                Offset(w * 0.27f, h * 0.06f), Size(w * 0.46f, h * 0.46f),
                style = Stroke(width = w * 0.075f, cap = StrokeCap.Round),
            )
            drawRoundRect(onAccent, Offset(w * 0.08f, h * 0.30f), Size(w * 0.84f, h * 0.66f), CornerRadius(w * 0.16f))
            drawCircle(soft, w * 0.13f, Offset(w * 0.5f, h * 0.63f))
        }
    }
}

@Composable
fun ForcePinScreen(vm: AuthViewModel, modifier: Modifier = Modifier) {
    val ui by vm.force.collectAsStateWithLifecycle()
    val tokens = KonozTheme_.colors
    val scheme = MaterialTheme.colorScheme
    val accent = tokens.signInAccent
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, unfocusedBorderColor = scheme.outline, cursorColor = accent)
    val pill = RoundedCornerShape(50)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 36.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.auth_force_title), style = MaterialTheme.typography.headlineSmall, color = accent)
        Text(stringResource(R.string.auth_force_hint), style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            value = ui.pin, onValueChange = vm::onForcePin, singleLine = true,
            placeholder = { Text(stringResource(R.string.auth_new_pin)) },
            visualTransformation = PasswordVisualTransformation(),
            shape = pill, colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ui.confirm, onValueChange = vm::onForceConfirm, singleLine = true,
            placeholder = { Text(stringResource(R.string.auth_confirm_pin)) },
            visualTransformation = PasswordVisualTransformation(),
            shape = pill, colors = fieldColors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
        )
        ui.error?.let { Text(errorText(it, 0, 0), color = scheme.error) }
        Button(
            onClick = vm::saveForcedPin,
            enabled = !ui.busy,
            shape = pill,
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = tokens.onSignInAccent),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text(stringResource(R.string.auth_save_pin), style = MaterialTheme.typography.titleMedium) }
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
