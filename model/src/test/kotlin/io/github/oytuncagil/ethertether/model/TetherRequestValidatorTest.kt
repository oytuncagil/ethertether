// SPDX-FileCopyrightText: 2026 Oytun Cagil Koltuk
// SPDX-License-Identifier: Apache-2.0 OR GPL-3.0-or-later

package io.github.oytuncagil.ethertether.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TetherRequestValidatorTest {
    private fun static(phone: String, client: String, prefix: String) =
        TetherInput(TetherMode.STATIC, phone, client, prefix)

    private fun errorsOf(input: TetherInput): Set<ValidationError> =
        (TetherRequestValidator.validate(input) as ValidationResult.Invalid).errors

    private fun valid(input: TetherInput, networks: List<Ipv4Subnet> = emptyList()) =
        TetherRequestValidator.validate(input, networks) as ValidationResult.Valid

    @Test
    fun linkLocalProfileIsValidWithoutWarnings() {
        val result = valid(static("169.254.0.42", "169.254.0.43", "16"))
        val addresses = result.request.addresses!!
        assertEquals("169.254.0.42", addresses.phone.toString())
        assertEquals("169.254.0.0/16", addresses.subnet.toString())
        assertEquals(TetherMode.STATIC, result.request.mode)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun dhcpModeIgnoresAddressFields() {
        val result = valid(TetherInput(TetherMode.DHCP, "garbage", "", "", SharingScope.SHARE_INTERNET))
        assertNull(result.request.addresses)
        assertEquals(TetherMode.DHCP, result.request.mode)
        assertEquals(SharingScope.SHARE_INTERNET, result.request.scope)
    }

    @Test
    fun prefixBoundsFollowAndroidsDhcpServer() {
        valid(static("10.0.0.1", "10.0.0.2", "16"))
        valid(static("10.0.0.1", "10.0.0.2", "30"))
        valid(static("10.0.0.1", "10.0.0.2", "/24"))
        val tooShort = TetherRequestValidator.validate(static("10.0.0.1", "10.0.0.2", "8")) as ValidationResult.Invalid
        assertEquals(setOf(ValidationError.PREFIX_TOO_SHORT), tooShort.errors)
        assertEquals(16, tooShort.suggestedPrefix)
        assertEquals(setOf(ValidationError.PREFIX_TOO_LONG), errorsOf(static("10.0.0.1", "10.0.0.2", "31")))
        assertEquals(setOf(ValidationError.PREFIX_INVALID), errorsOf(static("10.0.0.1", "10.0.0.2", "x")))
        assertEquals(setOf(ValidationError.PREFIX_INVALID), errorsOf(static("10.0.0.1", "10.0.0.2", "33")))
    }

    @Test
    fun noSuggestionUnlessThePrefixIsTooShort() {
        val result = TetherRequestValidator.validate(static("10.0.0.1", "10.0.0.2", "31")) as ValidationResult.Invalid
        assertNull(result.suggestedPrefix)
    }

    @Test
    fun clientMustShareThePhoneSubnet() {
        assertEquals(setOf(ValidationError.CLIENT_IN_OTHER_SUBNET), errorsOf(static("192.168.1.1", "192.168.2.1", "24")))
        assertEquals(setOf(ValidationError.CLIENT_SAME_AS_PHONE), errorsOf(static("192.168.1.1", "192.168.1.1", "24")))
    }

    @Test
    fun networkAndBroadcastAddressesAreRejected() {
        assertEquals(setOf(ValidationError.PHONE_IS_NETWORK_ADDRESS), errorsOf(static("192.168.1.0", "192.168.1.2", "24")))
        assertEquals(setOf(ValidationError.PHONE_IS_BROADCAST_ADDRESS), errorsOf(static("192.168.1.255", "192.168.1.2", "24")))
        assertEquals(setOf(ValidationError.CLIENT_IS_NETWORK_ADDRESS), errorsOf(static("192.168.1.1", "192.168.1.0", "24")))
        assertEquals(setOf(ValidationError.CLIENT_IS_BROADCAST_ADDRESS), errorsOf(static("192.168.1.1", "192.168.1.255", "24")))
        assertEquals(setOf(ValidationError.CLIENT_IS_BROADCAST_ADDRESS), errorsOf(static("10.0.0.1", "10.0.0.3", "30")))
    }

    @Test
    fun malformedAndSpecialAddressesAreRejected() {
        val errors = errorsOf(static("300.1.1.1", "abc", "24"))
        assertEquals(setOf(ValidationError.PHONE_ADDRESS_INVALID, ValidationError.CLIENT_ADDRESS_INVALID), errors)
        val both = setOf(ValidationError.PHONE_ADDRESS_UNUSABLE, ValidationError.CLIENT_ADDRESS_UNUSABLE)
        assertEquals(both, errorsOf(static("127.0.0.1", "127.0.0.2", "24")))
        assertEquals(both, errorsOf(static("0.0.0.1", "0.0.0.2", "24")))
        assertEquals(setOf(ValidationError.CLIENT_ADDRESS_UNUSABLE), errorsOf(static("10.0.0.1", "224.0.0.1", "24")))
        assertEquals(setOf(ValidationError.PHONE_ADDRESS_UNUSABLE), errorsOf(static("240.0.0.1", "10.0.0.2", "24")))
    }

    @Test
    fun warningsForUnusualRangesAndOverlaps() {
        assertEquals(setOf(ValidationWarning.PUBLIC_ADDRESS_RANGE), valid(static("8.8.8.1", "8.8.8.2", "24")).warnings)
        assertEquals(setOf(ValidationWarning.RESERVED_ADDRESS_RANGE), valid(static("100.64.0.1", "100.64.0.2", "24")).warnings)
        val wifi = Ipv4Subnet.of(Ipv4Address.parse("192.168.1.20")!!, 24)
        val overlap = valid(static("192.168.1.1", "192.168.1.2", "24"), listOf(wifi))
        assertEquals(setOf(ValidationWarning.OVERLAPS_PHONE_NETWORK), overlap.warnings)
        assertTrue(valid(static("192.168.42.1", "192.168.42.2", "24"), listOf(wifi)).warnings.isEmpty())
    }

    @Test
    fun scopeOptionsArePassedThrough() {
        val input = static("10.1.1.1", "10.1.1.2", "24").copy(scope = SharingScope.SHARE_INTERNET, allowScopeFallback = true)
        val request = valid(input).request
        assertEquals(SharingScope.SHARE_INTERNET, request.scope)
        assertTrue(request.allowScopeFallback)
    }
}
