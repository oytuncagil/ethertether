// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.model.InputField
import io.github.oytuncagil.ethertether.model.Ipv4Address
import io.github.oytuncagil.ethertether.model.Ipv4Subnet
import io.github.oytuncagil.ethertether.model.SharingScope
import io.github.oytuncagil.ethertether.model.TetherInput
import io.github.oytuncagil.ethertether.model.TetherMode
import io.github.oytuncagil.ethertether.model.TetherRequestValidator
import io.github.oytuncagil.ethertether.model.ValidationResult
import io.github.oytuncagil.ethertether.ui.result.text

/**
 * The request fields: mode, addresses with validation messages under each field, a suggested
 * prefix or client address when that helps, scope and the fallback switch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RequestForm(input: TetherInput, validation: ValidationResult, onInput: ((TetherInput) -> TetherInput) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(input.mode == TetherMode.STATIC, R.string.debug_mode_static) { onInput { it.copy(mode = TetherMode.STATIC) } }
        Chip(input.mode == TetherMode.DHCP, R.string.debug_mode_dhcp) { onInput { it.copy(mode = TetherMode.DHCP) } }
    }
    if (input.mode == TetherMode.STATIC) {
        StaticFields(input, validation, onInput)
    } else {
        Text(stringResource(R.string.form_dhcp_explained), style = MaterialTheme.typography.bodyMedium)
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(input.scope == SharingScope.LOCAL_ONLY, R.string.debug_scope_local) { onInput { it.copy(scope = SharingScope.LOCAL_ONLY) } }
        Chip(input.scope == SharingScope.SHARE_INTERNET, R.string.debug_scope_share) { onInput { it.copy(scope = SharingScope.SHARE_INTERNET) } }
    }
    Text(
        stringResource(if (input.scope == SharingScope.LOCAL_ONLY) R.string.form_scope_local_explained else R.string.form_scope_share_explained),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (input.scope == SharingScope.LOCAL_ONLY) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = input.allowScopeFallback, role = Role.Switch) { on -> onInput { it.copy(allowScopeFallback = on) } },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.debug_allow_fallback), modifier = Modifier.weight(1f))
            Switch(checked = input.allowScopeFallback, onCheckedChange = null)
        }
    }
}

@Composable
private fun StaticFields(input: TetherInput, validation: ValidationResult, onInput: ((TetherInput) -> TetherInput) -> Unit) {
    val errors = (validation as? ValidationResult.Invalid)?.errors.orEmpty()
    fun errorFor(field: InputField) = errors.firstOrNull { it.field == field }?.text()
    AddressField(input.phoneAddress, R.string.debug_phone_address, errorFor(InputField.PHONE_ADDRESS)) { v -> onInput { it.copy(phoneAddress = v) } }
    AddressField(input.clientAddress, R.string.debug_client_address, errorFor(InputField.CLIENT_ADDRESS)) { v -> onInput { it.copy(clientAddress = v) } }
    suggestClient(input)?.let { suggestion ->
        TextButton(onClick = { onInput { it.copy(clientAddress = suggestion) } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_use_client, suggestion))
        }
    }
    AddressField(input.prefixLength, R.string.debug_prefix, errorFor(InputField.PREFIX_LENGTH), number = true) { v -> onInput { it.copy(prefixLength = v) } }
    (validation as? ValidationResult.Invalid)?.suggestedPrefix?.let { prefix ->
        TextButton(onClick = { onInput { it.copy(prefixLength = prefix.toString()) } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_use_prefix, prefix))
        }
    }
    (validation as? ValidationResult.Valid)?.warnings?.forEach { Text(it.text().resolve(), color = MaterialTheme.colorScheme.tertiary) }
}

/**
 * A client address to offer: the next host address after the phone in its subnet (or the one
 * before it at the top of the subnet), when the phone address and prefix are valid and the
 * client field is not already a valid choice.
 */
fun suggestClient(input: TetherInput): String? {
    val phone = Ipv4Address.parse(input.phoneAddress) ?: return null
    val prefix = input.prefixLength.trim().removePrefix("/").toIntOrNull() ?: return null
    if (prefix !in TetherRequestValidator.MIN_PREFIX..TetherRequestValidator.MAX_PREFIX) return null
    if (TetherRequestValidator.validate(input) is ValidationResult.Valid) return null
    val subnet = Ipv4Subnet.of(phone, prefix)
    if (phone == subnet.network || phone == subnet.broadcast) return null
    val next = Ipv4Address(phone.bits + 1)
    val candidate = if (next != subnet.broadcast) next else Ipv4Address(phone.bits - 1)
    return candidate.takeIf { it != subnet.network }?.toString()
}

@Composable
private fun Chip(selected: Boolean, @StringRes label: Int, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) }, modifier = Modifier.heightIn(min = 48.dp))
}

@Composable
private fun AddressField(value: String, @StringRes label: Int, error: UiText?, number: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(32)) },
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(it.resolve()) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}
