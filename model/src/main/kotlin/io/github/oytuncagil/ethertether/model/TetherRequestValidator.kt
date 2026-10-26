// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

/** Raw user input for a tethering request, as typed. Blank addresses mean DHCP mode. */
data class TetherInput(
    val mode: TetherMode,
    val phoneAddress: String = "",
    val clientAddress: String = "",
    val prefixLength: String = "",
    val scope: SharingScope = SharingScope.LOCAL_ONLY,
    val allowScopeFallback: Boolean = false,
)

/** A field of [TetherInput] that a problem refers to. */
enum class InputField { PHONE_ADDRESS, CLIENT_ADDRESS, PREFIX_LENGTH }

/** Something that makes the input unusable. The app turns each into a translated message. */
enum class ValidationError(val field: InputField) {
    /** The phone address is not a dotted-quad IPv4 address. */
    PHONE_ADDRESS_INVALID(InputField.PHONE_ADDRESS),
    /** The phone address is loopback, multicast or otherwise not a host address. */
    PHONE_ADDRESS_UNUSABLE(InputField.PHONE_ADDRESS),
    /** The phone address is the network address of its subnet. */
    PHONE_IS_NETWORK_ADDRESS(InputField.PHONE_ADDRESS),
    /** The phone address is the broadcast address of its subnet. */
    PHONE_IS_BROADCAST_ADDRESS(InputField.PHONE_ADDRESS),
    /** The client address is not a dotted-quad IPv4 address. */
    CLIENT_ADDRESS_INVALID(InputField.CLIENT_ADDRESS),
    /** The client address is loopback, multicast or otherwise not a host address. */
    CLIENT_ADDRESS_UNUSABLE(InputField.CLIENT_ADDRESS),
    /** The client address is the network address of the subnet. */
    CLIENT_IS_NETWORK_ADDRESS(InputField.CLIENT_ADDRESS),
    /** The client address is the broadcast address of the subnet. */
    CLIENT_IS_BROADCAST_ADDRESS(InputField.CLIENT_ADDRESS),
    /** Phone and client use the same address. */
    CLIENT_SAME_AS_PHONE(InputField.CLIENT_ADDRESS),
    /** The client address is outside the phone's subnet. */
    CLIENT_IN_OTHER_SUBNET(InputField.CLIENT_ADDRESS),
    /** The prefix length is not a number from 0 to 32. */
    PREFIX_INVALID(InputField.PREFIX_LENGTH),

    /** Below 16: Android's DHCP server refuses it; the app suggests /16. */
    PREFIX_TOO_SHORT(InputField.PREFIX_LENGTH),

    /** Above 30: no room for two hosts. */
    PREFIX_TOO_LONG(InputField.PREFIX_LENGTH),
}

/** Something allowed but worth telling the user about. */
enum class ValidationWarning {
    /** Not a private, link-local or shared range: real internet hosts use these addresses. */
    PUBLIC_ADDRESS_RANGE,

    /** Documentation, benchmark or carrier-grade NAT ranges. */
    RESERVED_ADDRESS_RANGE,

    /** The subnet overlaps a network the phone is already connected to. */
    OVERLAPS_PHONE_NETWORK,
}

/** The outcome of validation: a request, or the errors that prevent one. */
sealed interface ValidationResult {
    /** Valid input, possibly with [warnings]. */
    data class Valid(val request: TetherRequest, val warnings: Set<ValidationWarning>) : ValidationResult

    /** Invalid input. [suggestedPrefix] is set when a different prefix would fix the input. */
    data class Invalid(val errors: Set<ValidationError>, val suggestedPrefix: Int? = null) : ValidationResult
}

/**
 * Checks tethering input against what Android accepts: IPv4 only, both
 * addresses in one subnet, prefix 16 to 30, neither address the network or broadcast address.
 */
object TetherRequestValidator {
    /** Smallest prefix length Android's DHCP server accepts. */
    const val MIN_PREFIX = 16

    /** Largest prefix length Android's DHCP server accepts. */
    const val MAX_PREFIX = 30

    /** Validates [input]; [phoneNetworks] are the subnets the phone already uses, for the overlap warning. */
    fun validate(input: TetherInput, phoneNetworks: List<Ipv4Subnet> = emptyList()): ValidationResult {
        if (input.mode == TetherMode.DHCP) {
            return ValidationResult.Valid(TetherRequest(null, input.scope, input.allowScopeFallback), emptySet())
        }
        val errors = mutableSetOf<ValidationError>()
        val phone = parseHost(input.phoneAddress, errors, ValidationError.PHONE_ADDRESS_INVALID, ValidationError.PHONE_ADDRESS_UNUSABLE)
        val client = parseHost(input.clientAddress, errors, ValidationError.CLIENT_ADDRESS_INVALID, ValidationError.CLIENT_ADDRESS_UNUSABLE)
        val prefix = parsePrefix(input.prefixLength, errors)
        if (phone != null && client != null && prefix != null) checkPair(phone, client, prefix, errors)
        if (errors.isNotEmpty() || phone == null || client == null || prefix == null) {
            val suggestion = if (ValidationError.PREFIX_TOO_SHORT in errors) MIN_PREFIX else null
            return ValidationResult.Invalid(errors, suggestion)
        }
        val addresses = StaticAddresses(phone, client, prefix)
        val request = TetherRequest(addresses, input.scope, input.allowScopeFallback)
        return ValidationResult.Valid(request, warningsFor(addresses, phoneNetworks))
    }

    private fun parseHost(
        text: String,
        errors: MutableSet<ValidationError>,
        invalid: ValidationError,
        unusable: ValidationError,
    ): Ipv4Address? {
        val address = Ipv4Address.parse(text)
        if (address == null) {
            errors += invalid
            return null
        }
        if (AddressRanges.isUnusableForHosts(address)) {
            errors += unusable
            return null
        }
        return address
    }

    private fun parsePrefix(text: String, errors: MutableSet<ValidationError>): Int? {
        val prefix = text.trim().removePrefix("/").toIntOrNull()
        when {
            prefix == null || prefix !in 0..32 -> errors += ValidationError.PREFIX_INVALID
            prefix < MIN_PREFIX -> errors += ValidationError.PREFIX_TOO_SHORT
            prefix > MAX_PREFIX -> errors += ValidationError.PREFIX_TOO_LONG
            else -> return prefix
        }
        return null
    }

    private fun checkPair(phone: Ipv4Address, client: Ipv4Address, prefix: Int, errors: MutableSet<ValidationError>) {
        val subnet = Ipv4Subnet.of(phone, prefix)
        if (phone == subnet.network) errors += ValidationError.PHONE_IS_NETWORK_ADDRESS
        if (phone == subnet.broadcast) errors += ValidationError.PHONE_IS_BROADCAST_ADDRESS
        when {
            client == phone -> errors += ValidationError.CLIENT_SAME_AS_PHONE
            client !in subnet -> errors += ValidationError.CLIENT_IN_OTHER_SUBNET
            client == subnet.network -> errors += ValidationError.CLIENT_IS_NETWORK_ADDRESS
            client == subnet.broadcast -> errors += ValidationError.CLIENT_IS_BROADCAST_ADDRESS
        }
    }

    private fun warningsFor(addresses: StaticAddresses, phoneNetworks: List<Ipv4Subnet>): Set<ValidationWarning> {
        val warnings = mutableSetOf<ValidationWarning>()
        val phone = addresses.phone
        if (AddressRanges.isReserved(phone)) {
            warnings += ValidationWarning.RESERVED_ADDRESS_RANGE
        } else if (!AddressRanges.isPrivateOrLinkLocal(phone)) {
            warnings += ValidationWarning.PUBLIC_ADDRESS_RANGE
        }
        if (phoneNetworks.any { it.overlaps(addresses.subnet) }) warnings += ValidationWarning.OVERLAPS_PHONE_NETWORK
        return warnings
    }
}
