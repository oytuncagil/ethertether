// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.ui.result

import io.github.oytuncagil.ethertether.R
import io.github.oytuncagil.ethertether.model.ValidationError
import io.github.oytuncagil.ethertether.model.ValidationWarning
import io.github.oytuncagil.ethertether.ui.common.UiText

/** The message for a validation error, shown under its field. */
fun ValidationError.text(): UiText = UiText(
    when (this) {
        ValidationError.PHONE_ADDRESS_INVALID -> R.string.error_phone_invalid
        ValidationError.PHONE_ADDRESS_UNUSABLE -> R.string.error_phone_unusable
        ValidationError.PHONE_IS_NETWORK_ADDRESS -> R.string.error_phone_network
        ValidationError.PHONE_IS_BROADCAST_ADDRESS -> R.string.error_phone_broadcast
        ValidationError.CLIENT_ADDRESS_INVALID -> R.string.error_client_invalid
        ValidationError.CLIENT_ADDRESS_UNUSABLE -> R.string.error_client_unusable
        ValidationError.CLIENT_IS_NETWORK_ADDRESS -> R.string.error_client_network
        ValidationError.CLIENT_IS_BROADCAST_ADDRESS -> R.string.error_client_broadcast
        ValidationError.CLIENT_SAME_AS_PHONE -> R.string.error_client_same
        ValidationError.CLIENT_IN_OTHER_SUBNET -> R.string.error_client_other_subnet
        ValidationError.PREFIX_INVALID -> R.string.error_prefix_invalid
        ValidationError.PREFIX_TOO_SHORT -> R.string.error_prefix_too_short
        ValidationError.PREFIX_TOO_LONG -> R.string.error_prefix_too_long
    },
)

/** The message for a validation warning. */
fun ValidationWarning.text(): UiText = UiText(
    when (this) {
        ValidationWarning.PUBLIC_ADDRESS_RANGE -> R.string.warning_public_range
        ValidationWarning.RESERVED_ADDRESS_RANGE -> R.string.warning_reserved_range
        ValidationWarning.OVERLAPS_PHONE_NETWORK -> R.string.warning_overlaps
    },
)
