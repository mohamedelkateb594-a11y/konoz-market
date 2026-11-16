package com.konoz.market.ui.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.konoz.market.R

// Login look (from the owner's reference): light header with an illustration and rounded bottom corners, the app name in bold
// purple, pill-shaped fields with a light border, a purple pill button, a pale strip at the bottom.
private val LoginPurple = Color(0xFF6C63FF)
private val HeaderLight = Color(0xFFE3E9F2)
private val FieldBorderLight = Color(0xFFE0E6F0)

@Composable
fun LoginScreen(vm: AuthViewModel, modifier: Modifier = Modifier) {
    val ui by vm.login.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    val pageColor = if (dark) MaterialTheme.colorScheme.background else Color.White
    val headerColor = if (dark) MaterialTheme.colorScheme.surfaceVariant else HeaderLight
    val borderColor = if (dark) MaterialTheme.colorScheme.outline else FieldBorderLight
    val purple = if (dark) Color(0xFF8E88FF) else LoginPurple
    val headerHeight = (LocalConfiguration.current.screenHeightDp * 0.38f).dp

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = purple,
        unfocusedBorderColor = borderColor,
        focusedContainerColor = pageColor,
        unfocusedContainerColor = pageColor,
        cursorColor = purple,
    )
    val pill = RoundedCornerShape(50)

    Column(modifier.fillMaxSize().background(pageColor).imePadding().verticalScroll(rememberScrollState())) {
        // ---- header: illustration on a light card with rounded bottom corners ----
        Box(
            Modifier.fillMaxWidth().height(headerHeight)
                .clip(RoundedCornerShape(bottomStart = 44.dp, bottomEnd = 44.dp))
                .background(headerColor),
        ) {
            Canvas(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp, bottom = 12.dp)) { drawLoginIllustration(purple) }
        }

        // ---- title and form ----
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 36.dp).padding(top = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp, fontWeight = FontWeight.ExtraBold),
                color = purple,
            )
            Text(
                stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
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
            ui.error?.let { Text(errorText(it, ui.attemptsLeft, ui.lockedMinutes), color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = vm::login,
                enabled = !ui.busy,
                shape = pill,
                colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) { Text(stringResource(R.string.auth_login), style = MaterialTheme.typography.titleMedium) }
        }

        // ---- pale strip at the bottom, like the reference ----
        Spacer(Modifier.height(36.dp))
        Box(Modifier.fillMaxWidth().height(28.dp).background(headerColor))
        Box(Modifier.fillMaxWidth().navigationBarsPadding().background(headerColor))
    }
}

/** A phone with a logo, two password lines and a button, a person pointing at it, and a leaf. Drawn from shapes, no image file. */
private fun DrawScope.drawLoginIllustration(purple: Color) {
    val h = size.height
    val phoneH = h * 0.92f
    val phoneW = phoneH * 0.46f
    val cx = size.width / 2f
    val left = cx - phoneW * 0.95f
    val top = (h - phoneH) / 2f
    val grey = Color(0xFFC9CDD6)
    val faint = Color(0xFFE6E9F0)
    val ink = Color(0xFF2B2D42)

    // leaf behind the phone
    rotate(-28f, pivot = Offset(left - phoneW * 0.08f, top + phoneH * 0.86f)) {
        drawOval(Color(0xFFF3F3F6), Offset(left - phoneW * 0.34f, top + phoneH * 0.68f), Size(phoneW * 0.22f, phoneH * 0.28f))
    }
    rotate(18f, pivot = Offset(left - phoneW * 0.02f, top + phoneH * 0.86f)) {
        drawOval(Color(0xFFF7F7F9), Offset(left - phoneW * 0.2f, top + phoneH * 0.7f), Size(phoneW * 0.2f, phoneH * 0.26f))
    }

    // phone body and notch
    drawRoundRect(Color.White, Offset(left, top), Size(phoneW, phoneH), CornerRadius(phoneW * 0.14f))
    drawRoundRect(faint, Offset(left, top), Size(phoneW, phoneH), CornerRadius(phoneW * 0.14f), style = Stroke(width = 3f))
    drawRoundRect(faint, Offset(left + phoneW * 0.32f, top), Size(phoneW * 0.36f, phoneH * 0.04f), CornerRadius(phoneW * 0.04f))

    // logo square with three circles
    val logoSide = phoneW * 0.46f
    val logoLeft = left + (phoneW - logoSide) / 2f
    val logoTop = top + phoneH * 0.15f
    drawRect(Color.White, Offset(logoLeft, logoTop), Size(logoSide, logoSide))
    drawRect(grey, Offset(logoLeft, logoTop), Size(logoSide, logoSide), style = Stroke(width = 2f))
    val r = logoSide * 0.15f
    val cy = logoTop + logoSide / 2f
    drawCircle(grey, r, Offset(logoLeft + logoSide * 0.28f, cy))
    drawCircle(Color(0xFFD6D9E0), r, Offset(logoLeft + logoSide * 0.42f, cy))
    drawCircle(purple, r, Offset(logoLeft + logoSide * 0.62f, cy))

    // two password lines with dots
    val lineLeft = left + phoneW * 0.14f
    val lineRight = left + phoneW * 0.86f
    for (i in 0..1) {
        val y = top + phoneH * (0.5f + i * 0.1f)
        for (d in 0..2) drawCircle(purple, 3.2f, Offset(lineLeft + 6f + d * 11f, y - 7f))
        drawLine(faint, Offset(lineLeft, y), Offset(lineRight, y), strokeWidth = 2f, cap = StrokeCap.Round)
    }
    drawRoundRect(purple, Offset(lineRight - phoneW * 0.24f, top + phoneH * 0.72f), Size(phoneW * 0.24f, phoneH * 0.035f), CornerRadius(3f))

    // the person, to the right of the phone
    val px = left + phoneW + phoneW * 0.5f
    val footY = top + phoneH * 0.96f
    val legH = phoneH * 0.3f
    val legW = phoneW * 0.09f
    val bodyH = phoneH * 0.26f
    val bodyW = phoneW * 0.26f
    val bodyTop = footY - legH - bodyH
    drawRoundRect(ink, Offset(px - legW * 1.1f, footY - legH), Size(legW, legH), CornerRadius(legW * 0.4f))
    drawRoundRect(ink, Offset(px + legW * 0.1f, footY - legH), Size(legW, legH), CornerRadius(legW * 0.4f))
    drawRoundRect(ink, Offset(px - legW * 1.3f, footY - legW * 0.3f), Size(legW * 1.5f, legW * 0.45f), CornerRadius(4f))
    drawRoundRect(ink, Offset(px - legW * 0.1f, footY - legW * 0.3f), Size(legW * 1.5f, legW * 0.45f), CornerRadius(4f))
    drawRoundRect(purple, Offset(px - bodyW / 2f, bodyTop), Size(bodyW, bodyH), CornerRadius(bodyW * 0.3f))
    drawLine(Color(0xFFF4B6B6), Offset(px - bodyW * 0.45f, bodyTop + bodyH * 0.25f), Offset(left + phoneW * 1.04f, bodyTop + bodyH * 0.18f), strokeWidth = 5f, cap = StrokeCap.Round)
    val headR = bodyW * 0.3f
    drawCircle(Color(0xFFF4B6B6), headR, Offset(px, bodyTop - headR * 1.15f))
    drawArc(ink, 180f, 180f, true, Offset(px - headR, bodyTop - headR * 2.25f), Size(headR * 2f, headR * 1.5f))
}

@Composable
fun ForcePinScreen(vm: AuthViewModel, modifier: Modifier = Modifier) {
    val ui by vm.force.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    val purple = if (dark) Color(0xFF8E88FF) else LoginPurple
    val borderColor = if (dark) MaterialTheme.colorScheme.outline else FieldBorderLight
    val fieldColors = OutlinedTextFieldDefaults.colors(focusedBorderColor = purple, unfocusedBorderColor = borderColor, cursorColor = purple)
    val pill = RoundedCornerShape(50)
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 36.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.auth_force_title), style = MaterialTheme.typography.headlineSmall, color = purple)
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
        ui.error?.let { Text(errorText(it, 0, 0), color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = vm::saveForcedPin,
            enabled = !ui.busy,
            shape = pill,
            colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White),
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
